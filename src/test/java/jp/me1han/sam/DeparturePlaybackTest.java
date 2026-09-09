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
        return program(alternate, "test:door");
    }
    private static DepartureProgram program(boolean alternate, String... doorClose) {
        DepartureProgram value = new DepartureProgram(alternate);
        value.melody = "test:melody";
        value.doorCloseSounds.addAll(Arrays.asList(doorClose));
        return value.interval(0.5);
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

        Timeline noDoor = new Timeline(program(false, new String[0]).interval(0), lengths(1, 5));
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

        expectInvalid(() -> { DepartureProgram p = program(false, new String[0]); p.melody = "unknown"; p.resolve(Collections.emptyMap()); });
        for (int invalid : new int[]{0, -1, 72001})
            expectInvalid(() -> { DepartureProgram p = program(false, new String[0]); p.melody = "x"; p.resolve(Collections.singletonMap("x", invalid)); });
        expectInvalid(() -> program(true).resolve(Collections.singletonMap("test:melody", 20)));
        DepartureProgram stale = program(true);
        stale.melodyTicks = 999; stale.doorCloseTicks = 999;
        check(stale.resolve(lengths(20, 5)).melodyTicks == 20 && stale.resolve(lengths(20, 5)).doorCloseTicks == 5,
            "Cached tick fields cannot override JSON lengths");
        expectInvalid(() -> new DepartureProgram(false).interval(-1));
        DepartureProgram unresolved = program(false, new String[0]); unresolved.melody = "x";
        DepartureProgram resolved = unresolved.resolve(Collections.singletonMap("x", 42));
        check(resolved.melodyTicks == 42, "sam_length duration is used");

        verifyScripts();
        verifyOrdinaryRepeatApi();
        verifyParts();
        verifyPackScripts();
        verifyPackets();
        System.out.println("Departure playback: " + checks + " checks passed");
    }

    private static void verifyParts() throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        engine.put("sam", new SAMScriptAPI());
        engine.eval("function samMain(tile) { var sounds = []; sounds.push('test:door');"
            + " sounds.push(sam.interval(0.25)); sounds.push('test:melody');"
            + " sounds.push(sam.interval(0.10)); sounds.push('test:door');"
            + " return sam.build('test:melody', sounds, sam.push()); }");
        AnnouncePackLoader.scriptEngines.put("parts-test.js", engine);
        AnnouncePackLoader.soundTicks.putAll(lengths(20, 5));
        DepartureProgram p = AnnouncePackLoader.runDepartureScript("parts-test.js", null);
        Timeline timeline = new Timeline(p);
        timeline.ticks(57);
        timeline.expect("0:test:melody", "20:stop", "20:test:door", "25:stop", "30:test:melody",
            "50:stop", "52:test:door", "57:stop", "57:finished");
        ByteBuf buf = Unpooled.buffer();
        try {
            PacketDepartureStart packet = new PacketDepartureStart(); packet.departure = p;
            packet.toBytes(buf);
            PacketDepartureStart received = new PacketDepartureStart(); received.fromBytes(buf);
            check(received.departure.doorCloseSounds.equals(p.doorCloseSounds)
                && received.departure.doorCloseDurations.equals(p.doorCloseDurations), "Parts and durations round trip");
        } finally { buf.release(); }
        Timeline canceled = new Timeline(p); canceled.ticks(26); canceled.sequence.cancel(); canceled.ticks(100);
        check(!canceled.events.contains("30:test:melody") && canceled.playing.isEmpty(), "Cancel suppresses later parts");
        expectInvalid(() -> ((DepartureProgram) new SAMScriptAPI().build("test:melody", Arrays.<Object>asList("missing"),
            new DepartureProgram(false))).resolve(lengths(20, 5)));
        check(new SAMScriptAPI().interval(0.129).ticks == 3, "Part interval truncates to hundredths and rounds to ticks");
        for (double invalid : new double[]{0, 0.009, -1, Double.NaN, Double.POSITIVE_INFINITY, 3600.01})
            expectInvalid(() -> new SAMScriptAPI().interval(invalid));
        AnnouncePackLoader.scriptEngines.remove("parts-test.js");
    }

    private static void verifyOrdinaryRepeatApi() {
        SAMScriptAPI api = new SAMScriptAPI();
        List<Object> body = Arrays.<Object>asList("test:one", "test:two");
        AnnounceData legacy = (AnnounceData) api.build("test:start", body, "test:arr");
        AnnounceData explicitOne = api.build("test:start", body, "test:arr", 1);
        AnnounceData repeated = api.build("test:start", body, "test:arr", 2);
        check(legacy.repeatCount == 1 && explicitOne.repeatCount == 1, "Three-argument build equals repeatCount 1");
        check(legacy.startMelo.equals(explicitOne.startMelo) && legacy.bodySounds.equals(explicitOne.bodySounds)
            && legacy.arrMelo.equals(explicitOne.arrMelo), "Three- and four-argument builds preserve identical content");
        check(repeated.repeatCount == 2 && repeated.bodySounds.equals(Arrays.asList("test:one", "test:two")),
            "Repeat count remains separate from body sounds");
        check(api.build(null, body, "test:arr", 2).startMelo == null, "Repeated announcement permits no start melody");
        check(api.build("test:start", body, null, 2).arrMelo == null, "Repeated announcement permits no loop melody");
        check(api.build("test:start", Collections.emptyList(), "test:arr", 2).bodySounds.isEmpty(),
            "Repeated announcement permits an empty body");
        for (int invalid : new int[]{0, -1, Integer.MIN_VALUE})
            check(api.build("test:start", body, "test:arr", invalid).repeatCount == 1,
                "Non-positive repeat count is normalized to one: " + invalid);
        check(api.build("test:start", body, "test:arr", Integer.MAX_VALUE).repeatCount
            == AnnounceData.MAX_REPEAT_COUNT, "Excessive repeat count is capped");
        check(new AnnounceData("test:start", Collections.singletonList("test:body"), "test:arr").repeatCount == 1,
            "Existing AnnounceData constructor defaults to one repeat");
        AnnounceData withInterval = api.build(null,
            Arrays.<Object>asList("test:one", api.interval(0.25), "test:two"), null, 2);
        check(withInterval.bodySounds.equals(Arrays.asList("test:one", "", "test:two"))
            && withInterval.bodyIntervalTicks.equals(Arrays.asList(0, 5, 0)),
            "Ordinary announcement accepts an exact-position interval");
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

        Timeline longDoor = new Timeline(program(true).tachikawa(true).interval(0), lengths(20, 40));
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

        Timeline noDoor = new Timeline(program(true, new String[0]).tachikawa(true));
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

        Timeline sameSound = new Timeline(program(true, "test:melody").tachikawa(true).interval(0));
        sameSound.ticks(5); sameSound.sequence.release(); sameSound.ticks(15);
        check(sameSound.playing.size() == 1 && sameSound.playing.containsKey(DepartureSequence.Channel.DOOR_CLOSE),
            "Identical sound IDs remain separate channels");
        sameSound.ticks(10);

        for (int stopAt : new int[]{6, 16, 21}) {
            Timeline canceled = new Timeline(program(true).tachikawa(true), lengths(20, 40));
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
        String departure = "function samMain(tile) { return sam.build('test:melody', [], sam.toggle().interval(0.059)); }";
        try {
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(path))) {
                scriptEntry(zip, "sam_length.json", "{\"test:melody\":{\"length\":1.23}}");
                scriptEntry(zip, "scripts/shared.js", departure + "function getDisplayName() { return 'new'; }");
                scriptEntry(zip, "departure/ignored.js", departure);
                scriptEntry(zip, "scripts/ordinary.js", "function samMain(t) { return sam.build(null, [], null); }");
                scriptEntry(zip, "scripts/sub/departure.js", departure);
            }
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path.toFile())) {
                AnnouncePackLoader.loadScripts(zip);
                java.util.zip.ZipEntry lengths = zip.getEntry("assets/stationannouncemod/sam_length.json");
                if (lengths != null) AnnouncePackLoader.parseLengthJson(zip.getInputStream(lengths));
            }
            check(AnnouncePackLoader.runDepartureScript("shared.js", null).melodyTicks == 25, "External JSON supplies chorus duration");
            check(AnnouncePackLoader.runDepartureScript("shared.js", null).intervalTicks == 1, "Shared folder and JS interval truncation");
            check(((Invocable) AnnouncePackLoader.scriptEngines.get("shared.js")).invokeFunction("getDisplayName").equals("new"),
                "Display name uses the shared script registry");
            check(AnnouncePackLoader.availableScripts.stream().filter(s -> s.fileName.equals("shared.js")).count() == 1,
                "No duplicate filename registration");
            check(!AnnouncePackLoader.scriptEngines.containsKey("ignored.js"), "Legacy departure folder is not loaded");
            check(AnnouncePackLoader.runDepartureScript("departure.js", null).alternate, "Subfolder scripts resolve by filename");
            check(AnnouncePackLoader.runScript("ordinary.js", null) != null, "Ordinary script runs from shared folder");
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(path))) {
                scriptEntry(zip, "scripts/shared.js", departure + "function getDisplayName() { return 'later'; }");
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
        for (String mode : new String[]{"push", "toggle", "tachikawa"}) {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            check(engine != null, "Java 8 Nashorn is required");
            engine.put("sam", new SAMScriptAPI());
            engine.eval("function samMain(tile) { var sounds = ['test:door']; return sam.build('test:melody', sounds, sam."
                + (mode.equals("push") ? "push()" : "toggle()")
                + ".tachikawa(" + mode.equals("tachikawa") + ")); }");
            Invocable inv = (Invocable) engine;
            DepartureProgram p = ((DepartureProgram) inv.invokeFunction("samMain", (Object) null)).resolve(lengths(20, 5));
            check(p.alternate == !mode.equals("push"), "Script mode: " + mode);
            check(p.finishChorus == mode.equals("tachikawa"), "Script chorus mode: " + mode);
        }
        java.util.Set<String> apiMethods = new java.util.HashSet<>();
        for (java.lang.reflect.Method method : SAMScriptAPI.class.getMethods()) apiMethods.add(method.getName());
        check(!apiMethods.contains("momentary") && !apiMethods.contains("alternate") && !apiMethods.contains("buildDeparture"),
            "Old departure API names are unavailable");
        java.util.Set<String> programMethods = new java.util.HashSet<>();
        for (java.lang.reflect.Method method : DepartureProgram.class.getMethods()) programMethods.add(method.getName());
        check(!programMethods.contains("melody") && !programMethods.contains("doorClose"), "Old chained audio methods are unavailable");
        ScriptEngine oldHandler = new ScriptEngineManager().getEngineByName("nashorn");
        oldHandler.put("sam", new SAMScriptAPI());
        oldHandler.eval("function configureDeparture(tile) { return null; }");
        AnnouncePackLoader.scriptEngines.put("old-handler.js", oldHandler);
        try {
            AnnouncePackLoader.runDepartureScript("old-handler.js", null);
            throw new AssertionError("Old configureDeparture handler was accepted");
        } catch (NoSuchMethodException expected) { checks++; }
        finally { AnnouncePackLoader.scriptEngines.remove("old-handler.js"); }
        check(new jp.me1han.sam.render.TileEntityDepartureMelody().scriptName.isEmpty(), "No bundled script default");
        String json = "{\"test:valid\":{\"length\":0.051},\"test:zero\":{\"length\":0},"
            + "\"test:negative\":{\"length\":-0.01},\"test:large\":{\"length\":3600.001},"
            + "\"test:nan\":{\"length\":\"NaN\"},\"test:missing\":{}}";
        AnnouncePackLoader.parseLengthJson(new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        check(AnnouncePackLoader.soundTicks.get("test:valid") == 2, "JSON seconds round up to ticks");
        for (String id : new String[]{"test:zero", "test:negative", "test:large", "test:nan", "test:missing"}) {
            DepartureProgram invalid = program(false, new String[0]); invalid.melody = id;
            expectInvalid(() -> invalid.resolve(AnnouncePackLoader.soundTicks));
        }
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
            PacketAnnounce ordinary = new PacketAnnounce(new AnnounceData("test:start", Collections.singletonList("test:body"), "test:loop", 2),
                "platform-1", true, 1, 2, 3);
            ordinary.toBytes(buf);
            PacketAnnounce ordinaryRead = new PacketAnnounce(); ordinaryRead.fromBytes(buf);
            check(ordinaryRead.bodySounds.equals(ordinary.bodySounds) && ordinaryRead.repeatCount == 2
                && buf.readableBytes() == 0, "Ordinary sequence and repeat count round trip");
            buf.clear();
            PacketAnnounce withInterval = new PacketAnnounce(new AnnounceData(null,
                Arrays.asList("test:body", "", "test:body"), Arrays.asList(0, 5, 0), null, 2),
                "platform-1", true, 1, 2, 3);
            withInterval.toBytes(buf);
            PacketAnnounce intervalRead = new PacketAnnounce(); intervalRead.fromBytes(buf);
            check(intervalRead.bodySounds.equals(withInterval.bodySounds)
                && intervalRead.bodyIntervalTicks.equals(Arrays.asList(0, 5, 0))
                && intervalRead.repeatCount == 2 && buf.readableBytes() == 0,
                "Ordinary interval positions and repeat count round trip");
            buf.clear();
            PacketDepartureControl control = new PacketDepartureControl(123L, true); control.toBytes(buf);
            PacketDepartureControl decoded = new PacketDepartureControl(); decoded.fromBytes(buf);
            check(decoded.cancel && decoded.sessionId == 123L, "Scoped cancellation serialized");
            buf.clear();
            PacketDepartureMelodyConfig config = new PacketDepartureMelodyConfig(1, 2, 3, "platform-1", "legacy", "departure_tachikawa.js"); config.toBytes(buf);
            PacketDepartureMelodyConfig configRead = new PacketDepartureMelodyConfig(); configRead.fromBytes(buf);
            check(configRead.scriptName.equals(config.scriptName) && configRead.soundId.equals("legacy"), "Script choice serialized");
            buf.clear();
            new PacketDepartureSwitchConfig(1, 2, 3, "platform-1", "melodysw_momentary_sample", 2, 0.25F, -0.5F, 1.25F).toBytes(buf);
            PacketDepartureSwitchConfig switchRead = new PacketDepartureSwitchConfig(); switchRead.fromBytes(buf);
            check(switchRead.x == 1 && switchRead.linkKey.equals("platform-1"), "Switch link serialized");
            check(switchRead.modelName.equals("melodysw_momentary_sample") && switchRead.rotationYaw == 2, "Switch model and yaw serialized");
            check(switchRead.offsetX == 0.25F && switchRead.offsetY == -0.5F && switchRead.offsetZ == 1.25F,
                "Switch offsets serialized");
        } finally { buf.release(); }
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void expectInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid configuration"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
}
