package jp.me1han.sam;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import jp.me1han.sam.api.AnnounceData;
import jp.me1han.sam.api.DepartureProgram;
import jp.me1han.sam.api.DepartureSequence;
import jp.me1han.sam.api.ScriptType;
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

    private static final class RestoredTimeline implements DepartureSequence.Output {
        final List<String> events = new ArrayList<>();
        final java.util.Set<DepartureSequence.Channel> playing =
            java.util.EnumSet.noneOf(DepartureSequence.Channel.class);
        int time;
        final DepartureSequence sequence;
        RestoredTimeline(DepartureProgram program, long elapsed, long release) {
            time = (int)elapsed;
            sequence = new DepartureSequence(program.resolve(lengths(20, 5)), this, elapsed, release);
        }
        public void play(DepartureSequence.Channel channel, String sound) {
            playing.add(channel); events.add(time + ":" + sound);
        }
        public void stop(DepartureSequence.Channel channel) {
            playing.remove(channel); events.add(time + ":stop-" + channel);
        }
        public void finished() { events.add(time + ":finished"); }
        void ticks(int count) { for (int i = 0; i < count; i++) { time++; sequence.tick(); } }
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
        verifyJavaScriptRuntime();
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
        verifyRetrigger();
        verifyIntervalPrecision();

        Timeline sameTickOff = new Timeline(program(true).interval(0));
        sameTickOff.sequence.release();
        sameTickOff.ticks(5);
        sameTickOff.expect("0:test:melody", "0:stop", "0:test:door", "5:stop", "5:finished");

        Timeline noDoor = new Timeline(program(false, new String[0]).interval(0), lengths(1, 5));
        noDoor.ticks(1);
        noDoor.expect("0:test:melody", "1:stop", "1:finished");

        RestoredTimeline restoredOn = new RestoredTimeline(program(true), 5, -1);
        check(restoredOn.events.isEmpty() && restoredOn.sequence.isOn(),
            "Late toggle restore does not replay the chorus already in progress");
        restoredOn.ticks(15);
        check(restoredOn.events.contains("20:test:melody"),
            "Late toggle restore starts at the next logical chorus boundary");

        RestoredTimeline restoredReleased = new RestoredTimeline(program(true), 7, 5);
        restoredReleased.ticks(8);
        check(restoredReleased.events.contains("15:test:door") && !restoredReleased.events.toString().contains("test:melody"),
            "Released restore observes the remaining interval without replaying melody");

        RestoredTimeline restoredFinishChorus = new RestoredTimeline(program(true).tachikawa(true), 7, 5);
        restoredFinishChorus.ticks(13);
        check(restoredFinishChorus.events.contains("15:test:door")
                && restoredFinishChorus.events.contains("20:finished"),
            "finishChorus restore advances melody and door-close channels to their shared logical finish");

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

    private static void verifyRetrigger() {
        Map<String, Integer> shortLengths = lengths(10, 6);
        Timeline sequence = new Timeline(program(true).interval(0.25), shortLengths);
        sequence.ticks(3);
        check(sequence.sequence.release() && !sequence.sequence.isOn(),
            "First OFF releases an alternate sequence");
        sequence.ticks(2);
        int intervalRemaining = sequence.sequence.getClosingRemaining();
        check(sequence.sequence.reengage() && sequence.sequence.isOn()
                && sequence.sequence.getClosingRemaining() == intervalRemaining,
            "Re-ON restarts a stopped melody without rewinding the active interval");
        check(sequence.events.equals(Arrays.asList("0:test:melody", "3:stop", "5:test:melody")),
            "finishChorus=false restarts the melody from its beginning on re-ON");
        sequence.ticks(intervalRemaining);
        check(sequence.sequence.getTailPhase() == DepartureSequence.TailPhase.DOOR_CLOSE
                && sequence.sequence.isMelodyPlaying()
                && sequence.playing.containsKey(DepartureSequence.Channel.MELODY)
                && sequence.playing.containsKey(DepartureSequence.Channel.DOOR_CLOSE),
            "Door-close and melody channels play concurrently after re-ON");
        int doorRemaining = sequence.sequence.getClosingRemaining();
        check(sequence.sequence.release()
                && sequence.sequence.getClosingRemaining() == doorRemaining
                && sequence.sequence.getClosingIndex() == 0,
            "A second OFF does not duplicate or restart an active door-close tail");
        sequence.ticks(doorRemaining);
        check(sequence.sequence.isFinished(), "The final OFF completes after both channels finish");
        check(sequence.events.stream().filter(e -> e.endsWith(":finished")).count() == 1,
            "Retriggered sequence emits normal completion exactly once");

        Timeline retained = new Timeline(program(true).interval(0.1), shortLengths);
        retained.ticks(1); retained.sequence.release(); retained.ticks(1); retained.sequence.reengage();
        retained.ticks(7);
        check(retained.sequence.getTailPhase() == DepartureSequence.TailPhase.DONE
                && retained.sequence.isOn() && !retained.sequence.isFinished(),
            "A completed first tail cannot finish a reengaged melody session");
        retained.sequence.release();
        check(retained.sequence.getTailPhase() == DepartureSequence.TailPhase.INTERVAL
                && retained.sequence.getClosingRemaining() == 2,
            "OFF after the previous tail completed starts a fresh interval and tail");
        retained.ticks(8);
        check(retained.sequence.isFinished()
                && retained.events.stream().filter(e -> e.endsWith(":test:door")).count() == 2,
            "A fresh post-retrigger OFF plays a second door-close tail and completes");

        Timeline chorus = new Timeline(program(true).tachikawa(true).interval(0.25), shortLengths);
        chorus.ticks(3); chorus.sequence.release(); chorus.ticks(2);
        int eventsBefore = chorus.events.size();
        int melodyBefore = chorus.sequence.getMelodyRemaining();
        check(!chorus.sequence.reengage() && chorus.sequence.isOn()
                && chorus.sequence.getMelodyRemaining() == melodyBefore
                && chorus.events.size() == eventsBefore,
            "finishChorus re-ON continues the current chorus without duplicate playback or restart");
        chorus.ticks(melodyBefore);
        check(chorus.events.stream().filter(e -> e.endsWith(":test:melody")).count() == 2,
            "The continued chorus loops normally once its boundary is reached");

        DepartureSequence.Snapshot snapshot = chorus.sequence.snapshot();
        final int[] restoredEvents = {0};
        DepartureSequence restored = new DepartureSequence(program(true).tachikawa(true).interval(0.25)
            .resolve(shortLengths), new DepartureSequence.Output() {
                public void play(DepartureSequence.Channel channel, String sound) { restoredEvents[0]++; }
                public void stop(DepartureSequence.Channel channel) { restoredEvents[0]++; }
                public void finished() { restoredEvents[0]++; }
            }, snapshot, 0);
        check(restoredEvents[0] == 0 && sameState(snapshot, restored.snapshot()),
            "Late join restores the current OFF/ON and independent tail state without replaying in-progress sounds");
        for (int i = 0; i < 4; i++) { chorus.sequence.tick(); restored.tick(); }
        check(sameState(chorus.sequence.snapshot(), restored.snapshot()),
            "Client and server sequences remain aligned after restoring multiple control transitions");

        Timeline rapid = new Timeline(program(true).interval(0), shortLengths);
        rapid.sequence.release(); rapid.sequence.reengage(); rapid.sequence.release(); rapid.sequence.reengage();
        rapid.sequence.cancel(); rapid.ticks(50);
        check(rapid.sequence.isFinished() && rapid.playing.isEmpty()
                && rapid.events.stream().noneMatch(e -> e.endsWith(":finished")),
            "Rapid controls followed by CANCEL stop both channels without a leaked completion");
    }

    private static boolean sameState(DepartureSequence.Snapshot a, DepartureSequence.Snapshot b) {
        return a.on == b.on && a.melodyPlaying == b.melodyPlaying
            && a.melodyRemaining == b.melodyRemaining && a.tailPhase == b.tailPhase
            && a.closingIndex == b.closingIndex && a.closingRemaining == b.closingRemaining;
    }

    private static void verifyJavaScriptRuntime() throws Exception {
        ScriptEngine engine = SamScriptEngineFactory.requireEngine();
        check(SamScriptEngineFactory.unavailableMessage().contains("Expected Java runtime: Java 8")
            && SamScriptEngineFactory.unavailableMessage().contains(System.getProperty("java.runtime.version")),
            "Nashorn-unavailable diagnostic identifies the required and actual runtime");
        engine.put("sam", new SAMScriptAPI());
        check(Boolean.TRUE.equals(engine.eval("typeof Java === 'undefined' && typeof Packages === 'undefined'"
            + " && typeof load === 'undefined' && typeof loadWithNewGlobal === 'undefined'"
            + " && typeof exit === 'undefined' && typeof quit === 'undefined'")),
            "Nashorn Java/package lookup and unnecessary host helpers are disabled");
        expectScriptRejected(engine, "Java.type('java.lang.System')");
        expectScriptRejected(engine, "Packages.java.lang.System.currentTimeMillis()");
        expectScriptRejected(engine, "load('anything.js')");
        expectScriptRejected(engine, "loadWithNewGlobal('anything.js')");
        expectScriptRejected(engine, "sam.getClass().forName('java.lang.System')");

        engine.eval("function getDisplayName() { return 'runtime-check'; }"
            + " function samMain(tile) {"
            + " if (String(tile.getLinkKey()) !== 'runtime' || String(tile.linkKey) !== 'runtime') throw 'link context failed';"
            + " if (String(tile.receivedData.get('key')) !== 'original') throw 'receivedData context failed';"
            + " if (!tile.receivedData.containsKey('key') || tile.receivedData.isEmpty() || tile.receivedData.size() !== 1) throw 'read queries failed';"
            + " try { tile.receivedData.put('key', 'changed'); } catch (expected) {}"
            + " if (typeof tile.getWorldObj !== 'undefined' || typeof tile.worldObj !== 'undefined') throw 'raw world leaked';"
            + " return sam.build(null, ['test:body'], null); }");
        Invocable invocable = (Invocable)engine;
        check("runtime-check".equals(invocable.invokeFunction("getDisplayName")),
            "Shared Nashorn provider invokes getDisplayName()");
        jp.me1han.sam.render.TileEntityAnnouncer tile =
            new jp.me1han.sam.render.TileEntityAnnouncer();
        tile.setLinkKey("runtime");
        tile.receivedData.put("key", "original");
        AnnouncePackLoader.soundTicks.put("test:body", 1);
        AnnouncePackLoader.scriptEngines.put("runtime-context.js", engine);
        AnnounceData value = AnnouncePackLoader.runScript("runtime-context.js", tile);
        check(value != null && value.bodySounds.equals(Collections.singletonList("test:body")),
            "Documented ordinary samMain(), sam.build(...) and read-only context work");
        check("original".equals(tile.receivedData.get("key")),
            "Script mutation cannot change the TileEntity receivedData map");

        ScriptEngine departureEngine = SamScriptEngineFactory.requireEngine();
        departureEngine.put("sam", new SAMScriptAPI());
        departureEngine.eval("function samMain(tile) {"
            + " if (String(tile.getLinkKey()) !== 'departure' || String(tile.linkKey) !== 'departure') throw 'link context failed';"
            + " if (typeof tile.receivedData !== 'undefined') throw 'departure receivedData leaked';"
            + " return sam.build('test:melody', ['test:door'], sam.push()); }");
        AnnouncePackLoader.scriptEngines.put("departure-context.js", departureEngine);
        AnnouncePackLoader.soundTicks.putAll(lengths(20, 5));
        jp.me1han.sam.render.TileEntityDepartureMelody departureTile =
            new jp.me1han.sam.render.TileEntityDepartureMelody();
        departureTile.setLinkKey("departure");
        check(AnnouncePackLoader.runDepartureScript("departure-context.js", departureTile).melodyTicks == 20,
            "Departure scripts receive linkKey but no receivedData or raw TileEntity");

        ScriptEngine wrong = SamScriptEngineFactory.requireEngine(); wrong.eval("function samMain(tile) { return null; }");
        AnnouncePackLoader.scriptEngines.put("wrong.js", wrong);
        check(AnnouncePackLoader.runScript("wrong.js", tile) == null, "Null/wrong ordinary return fails closed");
        ScriptEngine runtimeError = SamScriptEngineFactory.requireEngine();
        runtimeError.eval("function samMain(tile) { throw new Error('runtime-test'); }");
        AnnouncePackLoader.scriptEngines.put("runtime-error.js", runtimeError);
        check(AnnouncePackLoader.runScript("runtime-error.js", tile) == null,
            "JavaScript runtime exception is contained at the script boundary");
        ScriptEngine missing = SamScriptEngineFactory.requireEngine();
        AnnouncePackLoader.scriptEngines.put("missing.js", missing);
        check(AnnouncePackLoader.runScript("missing.js", tile) == null, "Missing samMain fails closed");
        ScriptEngine wrongDeparture = SamScriptEngineFactory.requireEngine();
        wrongDeparture.put("sam", new SAMScriptAPI());
        wrongDeparture.eval("function samMain(tile) { return sam.build(null, [], null); }");
        AnnouncePackLoader.scriptEngines.put("wrong-departure.js", wrongDeparture);
        try {
            AnnouncePackLoader.runDepartureScript("wrong-departure.js", departureTile);
            throw new AssertionError("Wrong departure return type was accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        AnnouncePackLoader.scriptEngines.remove("runtime-context.js");
        AnnouncePackLoader.scriptEngines.remove("departure-context.js");
        AnnouncePackLoader.scriptEngines.remove("wrong.js");
        AnnouncePackLoader.scriptEngines.remove("runtime-error.js");
        AnnouncePackLoader.scriptEngines.remove("missing.js");
        AnnouncePackLoader.scriptEngines.remove("wrong-departure.js");
    }

    private static void expectScriptRejected(ScriptEngine engine, String source) {
        try { engine.eval(source); throw new AssertionError("Restricted Nashorn host access was available: " + source); }
        catch (javax.script.ScriptException expected) { checks++; }
    }

    private static void verifyParts() throws Exception {
        ScriptEngine engine = SamScriptEngineFactory.requireEngine();
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
            PacketDepartureStart packet = new PacketDepartureStart(); packet.sessionId = 1; packet.departure = p;
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
        String oversizedSound = String.join("", Collections.nCopies(
            jp.me1han.sam.network.PacketLimits.NAME + 1, "s"));
        expectInvalid(() -> api.startmelo(oversizedSound));
        expectInvalid(() -> api.build(null, Collections.<Object>singletonList(oversizedSound), null));
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
        check(ScriptType.UNKNOWN.id == -1 && ScriptType.APPROACH.id == 0 && ScriptType.ARRIVAL.id == 1
                && ScriptType.STATION_NAME.id == 2 && ScriptType.DEPARTURE_MELODY.id == 3
                && ScriptType.AWARENESS.id == 4,
            "Script type identifiers remain stable and append-only");
        java.nio.file.Path path = java.nio.file.Files.createTempFile("sam-script-test-", ".zip");
        String departure = "function getScriptType() { return 3; }"
            + " function samMain(tile) { return sam.build('test:melody', [], sam.toggle().interval(0.059)); }";
        String legacyDeparture = "function samMain(tile) { return sam.build('test:melody', [], sam.push()); }";
        String hugeDisplayName = String.join("", Collections.nCopies(257, "x"));
        try {
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(path))) {
                scriptEntry(zip, "sam_length.json", "{\"test:melody\":{\"length\":1.23}}");
                scriptEntry(zip, "scripts/shared.js", departure + "function getDisplayName() { return 'new'; }");
                scriptEntry(zip, "departure/ignored.js", departure);
                scriptEntry(zip, "scripts/ordinary.js", "var typeCalls=0; function getScriptType(){typeCalls++;return 0;}"
                    + " function samMain(t) { return sam.build(null, [], null); }");
                scriptEntry(zip, "scripts/legacy-ordinary.js", "function samMain(t) { return sam.build(null, [], null); }");
                scriptEntry(zip, "scripts/legacy-departure.js", legacyDeparture);
                scriptEntry(zip, "scripts/invalid-type.js", "function getScriptType(){return 99;}"
                    + " function samMain(t) { return sam.build(null, [], null); }");
                scriptEntry(zip, "scripts/sub/departure.js", departure);
                scriptEntry(zip, "scripts/huge-name.js", "function getDisplayName(){return '" + hugeDisplayName
                    + "';} function samMain(tile){return sam.build(null, [], null);}");
                scriptEntry(zip, "scripts/missing-main.js", "function getDisplayName(){return 'invalid';}");
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
            check(AnnouncePackLoader.getScriptType("ordinary.js") == ScriptType.APPROACH
                    && AnnouncePackLoader.getScriptType("departure.js") == ScriptType.DEPARTURE_MELODY,
                "Pack loading caches declared stable script type IDs");
            check(AnnouncePackLoader.getScriptType("legacy-ordinary.js") == ScriptType.UNKNOWN
                    && AnnouncePackLoader.runScript("legacy-ordinary.js", null) != null
                    && AnnouncePackLoader.runDepartureScript("legacy-departure.js", null).melodyTicks == 25,
                "Scripts without getScriptType remain UNKNOWN and executable by either matching runtime");
            check(AnnouncePackLoader.getScriptType("invalid-type.js") == ScriptType.UNKNOWN,
                "Invalid getScriptType falls back to the cached UNKNOWN policy");
            check(AnnouncePackLoader.runScript("departure.js", null) == null,
                "Known departure scripts are rejected by the approach execution path");
            try {
                AnnouncePackLoader.runDepartureScript("ordinary.js", null);
                throw new AssertionError("Known approach script ran in the departure execution path");
            } catch (IllegalArgumentException expected) { checks++; }
            check(AnnouncePackLoader.availableScripts.stream().anyMatch(s -> s.isCompatibleWith(ScriptType.APPROACH)
                    && s.fileName.equals("ordinary.js"))
                    && AnnouncePackLoader.availableScripts.stream().anyMatch(s -> s.isCompatibleWith(ScriptType.APPROACH)
                        && s.fileName.equals("legacy-ordinary.js"))
                    && AnnouncePackLoader.availableScripts.stream().noneMatch(s -> s.isCompatibleWith(ScriptType.APPROACH)
                        && s.fileName.equals("departure.js")),
                "Approach GUI candidates include APPROACH and UNKNOWN but exclude departure scripts");
            check(AnnouncePackLoader.availableScripts.stream().anyMatch(s -> s.isCompatibleWith(ScriptType.DEPARTURE_MELODY)
                    && s.fileName.equals("departure.js"))
                    && AnnouncePackLoader.availableScripts.stream().anyMatch(s -> s.isCompatibleWith(ScriptType.DEPARTURE_MELODY)
                        && s.fileName.equals("legacy-departure.js"))
                    && AnnouncePackLoader.availableScripts.stream().noneMatch(s -> s.isCompatibleWith(ScriptType.DEPARTURE_MELODY)
                        && s.fileName.equals("ordinary.js")),
                "Departure GUI candidates include DEPARTURE_MELODY and UNKNOWN but exclude approach scripts");
            check(((Number)AnnouncePackLoader.scriptEngines.get("ordinary.js").eval("typeCalls")).intValue() == 1,
                "Compatibility checks and execution reuse the script type captured once at pack load");
            check(AnnouncePackLoader.availableScripts.stream().anyMatch(s -> s.fileName.equals("ordinary.js")
                && s.displayName.equals("ordinary.js")), "Missing getDisplayName falls back to the script filename");
            check(AnnouncePackLoader.availableScripts.stream().anyMatch(s -> s.fileName.equals("huge-name.js")
                && s.displayName.equals("huge-name.js")), "Oversized getDisplayName fails closed to the filename");
            check(!AnnouncePackLoader.scriptEngines.containsKey("missing-main.js"),
                "Pack loader rejects a script without required samMain");
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
            ScriptEngine engine = SamScriptEngineFactory.requireEngine();
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
        ScriptEngine oldHandler = SamScriptEngineFactory.requireEngine();
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
            ordinary.sessionId = 124L;
            Map<String, Integer> ordinaryLengths = new HashMap<>();
            ordinaryLengths.put("test:start", 7); ordinaryLengths.put("test:body", 11); ordinaryLengths.put("test:loop", 13);
            ordinary.resolveTiming(ordinaryLengths);
            ordinary.toBytes(buf);
            PacketAnnounce ordinaryRead = new PacketAnnounce(); ordinaryRead.fromBytes(buf);
            check(ordinaryRead.bodySounds.equals(ordinary.bodySounds) && ordinaryRead.repeatCount == 2
                && ordinaryRead.startMeloTicks == 7 && ordinaryRead.bodyPartTicks.equals(Collections.singletonList(11))
                && ordinaryRead.arrMeloTicks == 13
                && buf.readableBytes() == 0, "Ordinary sequence and repeat count round trip");
            buf.clear();
            PacketAnnounce withInterval = new PacketAnnounce(new AnnounceData(null,
                Arrays.asList("test:body", "", "test:body"), Arrays.asList(0, 5, 0), null, 2),
                "platform-1", true, 1, 2, 3);
            withInterval.sessionId = 125L;
            withInterval.resolveTiming(ordinaryLengths);
            withInterval.toBytes(buf);
            PacketAnnounce intervalRead = new PacketAnnounce(); intervalRead.fromBytes(buf);
            check(intervalRead.bodySounds.equals(withInterval.bodySounds)
                && intervalRead.bodyIntervalTicks.equals(Arrays.asList(0, 5, 0))
                && intervalRead.bodyPartTicks.equals(Arrays.asList(11, 5, 11))
                && intervalRead.repeatCount == 2 && buf.readableBytes() == 0,
                "Ordinary interval positions and repeat count round trip");
            for (PacketDepartureControl.Action action : PacketDepartureControl.Action.values()) {
                buf.clear();
                PacketDepartureControl control = new PacketDepartureControl(123L, action); control.toBytes(buf);
                PacketDepartureControl decoded = new PacketDepartureControl(); decoded.fromBytes(buf);
                check(decoded.action == action && decoded.sessionId == 123L,
                    "Scoped departure action serialized: " + action);
            }
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
