package jp.me1han.sam;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import jp.me1han.sam.api.AnnounceData;
import jp.me1han.sam.api.DepartureClick;
import jp.me1han.sam.api.DepartureProgram;
import jp.me1han.sam.api.DepartureSequence;
import jp.me1han.sam.network.PacketAnnounce;
import jp.me1han.sam.network.PacketDepartureStart;
import jp.me1han.sam.network.PacketDepartureControl;
import jp.me1han.sam.network.PacketDepartureMelodyConfig;
import jp.me1han.sam.network.PacketDepartureSwitchConfig;

/** No test framework dependency: exercised by check -> verifyDeparture. */
public final class DeparturePlaybackTest {
    private static int checks;
    private static java.util.Map<String, Integer> lengths(int melody, int door) {
        java.util.Map<String, Integer> values = new java.util.HashMap<>();
        values.put("test:melody", melody); values.put("test:door", door);
        return values;
    }
    private static final class Timeline implements DepartureSequence.Output {
        final List<String> events = new ArrayList<>();
        final java.util.Map<DepartureSequence.Channel, String> playing = new java.util.EnumMap<>(DepartureSequence.Channel.class);
        int time;
        DepartureSequence sequence;
        Timeline(DepartureProgram program) { this(program, lengths(20, 5)); }
        Timeline(DepartureProgram program, java.util.Map<String, Integer> lengths) {
            sequence = new DepartureSequence(program.resolve(lengths), this);
        }
        public void play(DepartureSequence.Channel channel, String sound) {
            check(!playing.containsKey(channel), "Channel must be stopped before replay");
            playing.put(channel, sound);
            events.add(time + ":" + sound);
        }
        public void stop(DepartureSequence.Channel channel) {
            check(playing.remove(channel) != null, "Stop only the active channel");
            events.add(time + ":stop");
        }
        public void finished() {
            check(playing.isEmpty(), "Completion waits for both audio channels");
            events.add(time + ":finished");
        }
        void ticks(int count) { for (int i = 0; i < count; i++) { time++; sequence.tick(); } }
        void expect(String... expected) { check(events.equals(Arrays.asList(expected)), events.toString()); }
    }

    private static DepartureProgram program(boolean alternate) {
        return new DepartureProgram(alternate).melody("test:melody").doorClose("test:door").interval(0.5);
    }

    public static void main(String[] args) throws Exception {
        Timeline momentary = new Timeline(program(false).interval(0));
        momentary.ticks(19);
        momentary.expect("0:test:melody");
        momentary.ticks(1);
        momentary.expect("0:test:melody", "20:stop", "20:test:door");
        momentary.ticks(5);
        momentary.expect("0:test:melody", "20:stop", "20:test:door", "25:stop", "25:finished");
        momentary.ticks(100);
        check(momentary.events.size() == 5, "Completion must fire once");

        Timeline alternate = new Timeline(program(true));
        alternate.ticks(45);
        alternate.sequence.release();
        alternate.sequence.release();
        alternate.ticks(15);
        alternate.expect("0:test:melody", "20:stop", "20:test:melody", "40:stop", "40:test:melody",
            "45:stop", "55:test:door", "60:stop", "60:finished");

        Timeline tachikawa = new Timeline(program(true).tachikawa(true));
        tachikawa.ticks(5);
        tachikawa.sequence.release();
        check(!tachikawa.sequence.isOn(), "Tachikawa switch is OFF while chorus continues");
        tachikawa.ticks(14);
        tachikawa.expect("0:test:melody", "15:test:door");
        tachikawa.ticks(16);
        tachikawa.expect("0:test:melody", "15:test:door", "20:stop", "20:stop", "20:finished");
        verifyTachikawa();
        verifyIntervalPrecision();

        Timeline sameTickOff = new Timeline(program(true).interval(0));
        sameTickOff.sequence.release();
        sameTickOff.ticks(5);
        sameTickOff.expect("0:test:melody", "0:stop", "0:test:door", "5:stop", "5:finished");

        Timeline noDoor = new Timeline(new DepartureProgram(false).melody("test:melody"), lengths(1, 5));
        noDoor.ticks(1);
        noDoor.expect("0:test:melody", "1:stop", "1:finished");

        for (int stopAt : new int[]{1, 22, 31}) {
            Timeline canceled = new Timeline(program(false));
            canceled.ticks(stopAt);
            canceled.sequence.cancel();
            int events = canceled.events.size();
            canceled.ticks(100);
            check(canceled.sequence.isFinished() && canceled.events.size() == events, "No revival after emergency stop");
            check(!canceled.events.toString().contains("finished"), "Cancellation must not notify normal completion");
        }

        // Restart during the interval: old door-close must never escape its canceled sequence.
        Timeline old = new Timeline(program(true));
        old.ticks(3); old.sequence.release(); old.ticks(2); old.sequence.cancel(); old.ticks(50);
        check(!old.events.toString().contains("test:door"), "No stale door-close on re-ON");

        expectInvalid(() -> new DepartureProgram(false).melody("unknown").resolve(Collections.emptyMap()));
        for (int invalid : new int[]{0, -1, 72001})
            expectInvalid(() -> new DepartureProgram(false).melody("x").resolve(Collections.singletonMap("x", invalid)));
        expectInvalid(() -> program(true).resolve(Collections.singletonMap("test:melody", 20)));
        DepartureProgram stale = program(true);
        stale.melodyTicks = 999; stale.doorCloseTicks = 999;
        check(stale.resolve(lengths(20, 5)).melodyTicks == 20 && stale.resolve(lengths(20, 5)).doorCloseTicks == 5,
            "Cached tick fields cannot override JSON lengths");
        expectInvalid(() -> new DepartureProgram(false).interval(-1));
        DepartureProgram resolved = new DepartureProgram(false).melody("x").resolve(Collections.singletonMap("x", 42));
        check(resolved.melodyTicks == 42, "sam_length duration is used");

        verifyScripts();
        verifyPackScripts();
        verifyPackets();
        System.out.println("Departure playback: " + checks + " checks passed");
    }

    private static void verifyTachikawa() {
        Timeline earlyDoor = new Timeline(program(true).tachikawa(true).interval(0));
        earlyDoor.ticks(5); earlyDoor.sequence.release(); earlyDoor.sequence.release();
        check(earlyDoor.playing.size() == 2, "OFF starts overlapping door-close immediately");
        earlyDoor.ticks(5);
        check(earlyDoor.playing.containsKey(DepartureSequence.Channel.MELODY)
            && earlyDoor.playing.size() == 1 && !earlyDoor.sequence.isFinished(), "Short door-close leaves chorus playing");
        earlyDoor.ticks(10);
        earlyDoor.expect("0:test:melody", "5:test:door", "10:stop", "20:stop", "20:finished");

        Timeline longDoor = new Timeline(program(true).tachikawa(true).interval(0).doorClose("test:door"), lengths(20, 40));
        longDoor.ticks(5); longDoor.sequence.release(); longDoor.ticks(15);
        check(longDoor.playing.size() == 1 && longDoor.playing.containsKey(DepartureSequence.Channel.DOOR_CLOSE),
            "Chorus end leaves long door-close playing");
        longDoor.ticks(25);
        longDoor.expect("0:test:melody", "5:test:door", "20:stop", "45:stop", "45:finished");

        Timeline lateDoor = new Timeline(program(true).tachikawa(true).interval(2));
        lateDoor.ticks(5); lateDoor.sequence.release(); lateDoor.ticks(15);
        check(lateDoor.playing.isEmpty() && !lateDoor.sequence.isFinished(), "Wait for interval after chorus ends");
        lateDoor.ticks(30);
        lateDoor.expect("0:test:melody", "20:stop", "45:test:door", "50:stop", "50:finished");

        Timeline noDoor = new Timeline(program(true).tachikawa(true).doorClose(""));
        noDoor.ticks(5); noDoor.sequence.release(); noDoor.ticks(15);
        noDoor.expect("0:test:melody", "20:stop", "20:finished");

        Timeline immediateOff = new Timeline(program(true).tachikawa(true).interval(0));
        immediateOff.sequence.release(); immediateOff.ticks(20);
        immediateOff.expect("0:test:melody", "0:test:door", "5:stop", "20:stop", "20:finished");

        Timeline boundary = new Timeline(program(true).tachikawa(true).interval(0));
        boundary.ticks(19); boundary.sequence.release(); boundary.ticks(5);
        boundary.expect("0:test:melody", "19:test:door", "20:stop", "24:stop", "24:finished");

        Timeline updateOff = new Timeline(program(true).tachikawa(true));
        updateOff.ticks(19); updateOff.time++; updateOff.sequence.release(); updateOff.sequence.tick(true);
        updateOff.ticks(15);
        updateOff.expect("0:test:melody", "20:stop", "30:test:door", "35:stop", "35:finished");

        Timeline sameSound = new Timeline(program(true).tachikawa(true).interval(0).doorClose("test:melody"));
        sameSound.ticks(5); sameSound.sequence.release(); sameSound.ticks(15);
        check(sameSound.playing.size() == 1 && sameSound.playing.containsKey(DepartureSequence.Channel.DOOR_CLOSE),
            "Identical sound IDs remain separate channels");
        sameSound.ticks(10);

        for (int stopAt : new int[]{6, 16, 21}) {
            Timeline canceled = new Timeline(program(true).tachikawa(true).doorClose("test:door"), lengths(20, 40));
            canceled.ticks(5); canceled.sequence.release(); canceled.ticks(stopAt - 5);
            canceled.sequence.cancel();
            check(canceled.playing.isEmpty(), "Cancellation stops every active channel");
            int eventCount = canceled.events.size(); canceled.ticks(100);
            check(canceled.events.size() == eventCount && !canceled.events.toString().contains("finished"),
                "Canceled overlap never resumes or notifies completion");
            Timeline restarted = new Timeline(program(true).tachikawa(true));
            restarted.expect("0:test:melody");
        }
        int events = earlyDoor.events.size(); earlyDoor.ticks(100);
        check(events == earlyDoor.events.size(), "Overlapping completion fires exactly once");
    }

    private static void verifyIntervalPrecision() {
        double[] seconds = {0, 0.009, 0.01, 0.05, 0.059, 0.12, 0.129, 0.29, 3600};
        int[] ticks = {0, 0, 1, 1, 1, 3, 3, 6, 72000};
        for (int i = 0; i < seconds.length; i++)
            check(program(true).interval(seconds[i]).intervalTicks == ticks[i], "Decimal truncation: " + seconds[i]);
        for (double value : new double[]{-0.001, Double.NaN, Double.POSITIVE_INFINITY, 3600.001})
            expectInvalid(() -> program(true).interval(value));
    }

    private static void verifyPackScripts() throws Exception {
        java.nio.file.Path path = java.nio.file.Files.createTempFile("sam-script-test-", ".zip");
        String departure = "function configureDeparture(d) { return sam.alternate().melody('test:melody').interval(0.059); }"
            + "function onDepartureClick(c) { c.toggle(); }";
        try {
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(path))) {
                scriptEntry(zip, "sam_length.json", "{\"test:melody\":{\"length\":1.23}}");
                // Intentionally put scripts/ first: precedence must not depend on entry order.
                scriptEntry(zip, "scripts/shared.js", departure + "function samMain(t) { return null; } function getDisplayName() { return 'new'; }");
                scriptEntry(zip, "departure/shared.js", "function getDisplayName() { return 'legacy'; }");
                scriptEntry(zip, "departure/legacy.js", departure);
                scriptEntry(zip, "scripts/ordinary.js", "function samMain(t) { return sam.build(null, [], null); }");
                scriptEntry(zip, "scripts/sub/departure.js", departure);
            }
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path.toFile())) {
                AnnouncePackLoader.loadScripts(zip);
                java.util.zip.ZipEntry lengths = zip.getEntry("assets/stationannouncemod/sam_length.json");
                if (lengths != null) AnnouncePackLoader.parseLengthJson(zip.getInputStream(lengths));
            }
            check(AnnouncePackLoader.configureDeparture("shared.js", null).melodyTicks == 25, "External JSON supplies chorus duration");
            check(AnnouncePackLoader.configureDeparture("shared.js", null).intervalTicks == 1, "Shared folder and JS interval truncation");
            check(((Invocable) AnnouncePackLoader.scriptEngines.get("shared.js")).invokeFunction("getDisplayName").equals("new"),
                "scripts/ overrides legacy folder in same ZIP");
            check(AnnouncePackLoader.availableScripts.stream().filter(s -> s.fileName.equals("shared.js")).count() == 1,
                "No duplicate filename registration");
            check(AnnouncePackLoader.configureDeparture("legacy.js", null).alternate, "Legacy departure folder still works");
            check(AnnouncePackLoader.configureDeparture("departure.js", null).alternate, "Subfolder scripts resolve by filename");
            check(AnnouncePackLoader.runScript("ordinary.js", null) != null, "Ordinary script runs from shared folder");
            DepartureClick click = new DepartureClick(false, true);
            AnnouncePackLoader.clickDeparture("shared.js", click);
            check(click.getAction() == DepartureClick.Action.ON, "Shared registry dispatches departure clicks");
            // A later pack retains the existing last-loaded-wins behavior, even using the legacy folder.
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(path))) {
                scriptEntry(zip, "departure/shared.js", departure + "function getDisplayName() { return 'later'; }");
            }
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path.toFile())) { AnnouncePackLoader.loadScripts(zip); }
            check(((Invocable) AnnouncePackLoader.scriptEngines.get("shared.js")).invokeFunction("getDisplayName").equals("later"),
                "Later pack overrides earlier pack");
        } finally { java.nio.file.Files.deleteIfExists(path); }
    }

    private static void scriptEntry(java.util.zip.ZipOutputStream zip, String name, String source) throws Exception {
        zip.putNextEntry(new java.util.zip.ZipEntry("assets/stationannouncemod/" + name));
        zip.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void verifyScripts() throws Exception {
        for (String mode : new String[]{"momentary", "alternate", "tachikawa"}) {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            check(engine != null, "Java 8 Nashorn is required");
            engine.put("sam", new SAMScriptAPI());
            engine.eval("function configureDeparture(d) { return sam."
                + (mode.equals("momentary") ? "momentary()" : "alternate()")
                + ".melody('test:melody').doorClose('test:door').tachikawa(" + mode.equals("tachikawa") + "); }"
                + "function onDepartureClick(c) { c.toggle(); }");
            Invocable inv = (Invocable) engine;
            DepartureProgram p = ((DepartureProgram) inv.invokeFunction("configureDeparture", (Object) null)).resolve(lengths(20, 5));
            check(p.alternate == !mode.equals("momentary"), "Script mode: " + mode);
            check(p.finishChorus == mode.equals("tachikawa"), "Script chorus mode: " + mode);
            DepartureClick first = new DepartureClick(false, p.alternate);
            inv.invokeFunction("onDepartureClick", first);
            check(first.getAction() == (p.alternate ? DepartureClick.Action.ON : DepartureClick.Action.PRESS), "First click");
            DepartureClick second = new DepartureClick(true, p.alternate);
            inv.invokeFunction("onDepartureClick", second);
            check(second.getAction() == (p.alternate ? DepartureClick.Action.OFF : DepartureClick.Action.PRESS), "Second click");
        }
        for (java.lang.reflect.Method method : DepartureProgram.class.getMethods())
            if (method.getName().equals("melody") || method.getName().equals("doorClose"))
                check(method.getParameterCount() == 1, "Audio APIs accept only an ID");
        check(new jp.me1han.sam.render.TileEntityDepartureMelody().scriptName.isEmpty(), "No bundled script default");
        String json = "{\"test:valid\":{\"length\":0.051},\"test:zero\":{\"length\":0},"
            + "\"test:negative\":{\"length\":-0.01},\"test:large\":{\"length\":3600.001},"
            + "\"test:nan\":{\"length\":\"NaN\"},\"test:missing\":{}}";
        AnnouncePackLoader.parseLengthJson(new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        check(AnnouncePackLoader.soundTicks.get("test:valid") == 2, "JSON seconds round up to ticks");
        for (String id : new String[]{"test:zero", "test:negative", "test:large", "test:nan", "test:missing"})
            expectInvalid(() -> new DepartureProgram(false).melody(id).resolve(AnnouncePackLoader.soundTicks));
    }

    private static void verifyPackets() {
        ByteBuf buf = Unpooled.buffer();
        try {
            PacketDepartureStart outgoing = new PacketDepartureStart();
            outgoing.sessionId = 123L; outgoing.linkKey = "platform-1";
            outgoing.x = 1; outgoing.y = 2; outgoing.z = 3;
            outgoing.targets = new long[] { SpeakerRegistry.position(10, 20, 30) };
            outgoing.departure = program(true).tachikawa(true).resolve(lengths(20, 5));
            outgoing.toBytes(buf);
            PacketDepartureStart received = new PacketDepartureStart(); received.fromBytes(buf);
            check(received.departure.finishChorus && received.departure.alternate, "Playback mode serialized");
            check(received.departure.melodyTicks == 20 && received.departure.doorCloseTicks == 5 && received.departure.intervalTicks == 10, "Durations serialized");
            check(SpeakerRegistry.x(received.targets[0]) == 10 && received.sessionId == 123L, "Compact target and session serialized");
            check(buf.readableBytes() == 0, "Playback packet completely consumed");
            buf.clear();
            PacketAnnounce ordinary = new PacketAnnounce(new AnnounceData("test:start", Collections.singletonList("test:body"), "test:loop"),
                "platform-1", true, 1, 2, 3);
            ordinary.toBytes(buf);
            PacketAnnounce ordinaryRead = new PacketAnnounce(); ordinaryRead.fromBytes(buf);
            check(ordinaryRead.bodySounds.equals(ordinary.bodySounds) && buf.readableBytes() == 0, "Ordinary sequence round trip");
            buf.clear();
            PacketDepartureControl control = new PacketDepartureControl(123L, true); control.toBytes(buf);
            PacketDepartureControl decoded = new PacketDepartureControl(); decoded.fromBytes(buf);
            check(decoded.cancel && decoded.sessionId == 123L, "Scoped cancellation serialized");
            buf.clear();
            PacketDepartureMelodyConfig config = new PacketDepartureMelodyConfig(1, 2, 3, "platform-1", "legacy", "departure_tachikawa.js"); config.toBytes(buf);
            PacketDepartureMelodyConfig configRead = new PacketDepartureMelodyConfig(); configRead.fromBytes(buf);
            check(configRead.scriptName.equals(config.scriptName) && configRead.soundId.equals("legacy"), "Script choice serialized");
            buf.clear();
            new PacketDepartureSwitchConfig(1, 2, 3, "platform-1", "sam_push", 2).toBytes(buf);
            PacketDepartureSwitchConfig switchRead = new PacketDepartureSwitchConfig(); switchRead.fromBytes(buf);
            check(switchRead.x == 1 && switchRead.linkKey.equals("platform-1"), "Switch link serialized");
            check(switchRead.modelName.equals("sam_push") && switchRead.rotationYaw == 2, "Switch model and yaw serialized");
        } finally { buf.release(); }
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void expectInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid configuration"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
}
