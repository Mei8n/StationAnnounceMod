package jp.me1han.sam.network;

import java.lang.reflect.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.*;
import io.netty.handler.codec.DecoderException;
import jp.me1han.sam.*;
import jp.me1han.sam.api.*;
import jp.me1han.sam.client.AnnounceManager;
import jp.me1han.sam.render.*;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;

/** Headless tests exercise production dispatch, routing, lifecycle and playback, without RTM. */
public final class NetworkVerificationTest {
    private static int checks;
    private static int playerId;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Method mapping = TileEntity.class.getDeclaredMethod("addMapping", Class.class, String.class);
        mapping.setAccessible(true);
        mapping.invoke(null, Speaker.class, "network-test-speaker");
        mapping.invoke(null, TileEntityAnnouncer.class, "network-test-announcer");
        mapping.invoke(null, TileEntityStartAnnouncer.class, "network-test-start");
        mapping.invoke(null, TileEntityStopAnnouncer.class, "network-test-stop");
        mapping.invoke(null, TileEntityTrainTypeSelector.class, "network-test-selector");
        mapping.invoke(null, TileEntityDebugReceiver.class, "network-test-debug");
        mapping.invoke(null, TileEntityAwarenessAnnouncer.class, "network-test-awareness");
        lifecycle(); wireBounds(); delivery(); config(); client(); ordinaryRepeats(); limitsAndExpiry(); fallbackAuthority();
        SpeakerRegistry.clear(); LoadedSamTiles.clear(); ServerSessions.clear();
        System.out.println("Network verification: " + checks + " checks passed");
    }

    private static void lifecycle() {
        FixtureWorld world = new FixtureWorld();
        List<Speaker> speakers = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            Speaker speaker = new Speaker(); speaker.linkKey = i < 125 ? "A" : "B";
            world.add(speaker, i, 0, -i); speakers.add(speaker);
        }
        check(SpeakerRegistry.findByKey(world, " A ").size() == 125, "125 A speakers registered by validate");
        SpeakerRegistry.Entry original = SpeakerRegistry.findByKey(world, "A").iterator().next();
        for (int tick = 0; tick < 100; tick++) for (Speaker speaker : speakers) {
            speaker.updateEntity();
        }
        check(speakers.stream().noneMatch(Speaker::canUpdate), "All 150 speakers excluded from ticking list");
        check(SpeakerRegistry.findByKey(world, "A").contains(original), "No replacement registry entries after 15000 idle calls");
        Speaker first = speakers.get(0);
        first.onChunkUnload();
        check(SpeakerRegistry.findByKey(world, "A").size() == 124, "Unload unregisters");
        first.validate(); first.validate();
        check(SpeakerRegistry.findByKey(world, "A").size() == 125, "Reload/duplicate validate is idempotent");
        Speaker replacement = new Speaker(); replacement.linkKey = "A"; world.add(replacement, 0, 0, 0);
        first.invalidate();
        check(SpeakerRegistry.findByKey(world, "A").size() == 125, "Old invalidation cannot remove replacement");
        check(replacement.applyConfig("B", 32, .5F), "Changed speaker config accepted");
        check(SpeakerRegistry.findByKey(world, "A").size() == 124 && SpeakerRegistry.findByKey(world, "B").size() == 26, "Both key indexes updated");
        int updates = world.updates, dirty = replacement.dirty;
        check(!replacement.applyConfig("B", 32, .5F) && updates == world.updates && dirty == replacement.dirty, "Unchanged config emits nothing");
        replacement.invalidate();
        check(SpeakerRegistry.findByKey(world, "B").size() == 25, "Block destruction removes entry");
        FixtureWorld another = new FixtureWorld();
        check(SpeakerRegistry.findByKey(another, "A").isEmpty(), "Same dimension different world is isolated");
        another.isRemote = true;
        Speaker remote = new Speaker(); remote.linkKey = "A"; another.add(remote, 0, 0, 0);
        check(SpeakerRegistry.findByKey(another, "A").isEmpty(), "Client TE never enters server registry");
        for (int x : new int[] {-30000000, -1, 0, 30000000}) for (int z : new int[] {-30000000, -1, 0, 30000000}) {
            long pos = SpeakerRegistry.position(x, 255, z);
            check(SpeakerRegistry.x(pos) == x && SpeakerRegistry.z(pos) == z && SpeakerRegistry.y(pos) == 255, "Position packing at world limits");
        }
        SpeakerRegistry.clear(world);
        check(SpeakerRegistry.findByKey(world, "A").isEmpty(), "World unload cleanup");
    }

    private static void wireBounds() {
        for (float volume : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.1F, 1.1F})
            check(!PacketLimits.speaker(16, volume), "Invalid volume rejected");
        check(!PacketLimits.speaker(0, 1) && !PacketLimits.speaker(PacketLimits.MAX_RANGE+1, 1), "Range bounds");
        check(PacketLimits.speaker(1, 0) && PacketLimits.speaker(PacketLimits.MAX_RANGE, 1), "Range/volume endpoints");
        ByteBuf buf = Unpooled.buffer();
        try {
            for (int size : new int[] {-1, Integer.MAX_VALUE, PacketLimits.CONDITIONS+1}) {
                buf.clear(); buf.writeInt(0).writeInt(0).writeInt(0).writeInt(size);
                expectInvalid(() -> new PacketTrainTypeConfig().fromBytes(buf));
            }
            buf.clear(); buf.writeInt(0).writeInt(0).writeInt(0);
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, String.join("", Collections.nCopies(65, "x")));
            buf.writeInt(16).writeFloat(1);
            expectInvalid(() -> new PacketSpeakerConfig().fromBytes(buf));
            buf.clear(); cpw.mods.fml.common.network.ByteBufUtils.writeVarInt(buf, 10000, 2);
            expectInvalid(() -> PacketLimits.readString(buf, 64));
            buf.clear(); new PacketAnnounceStop(15L).toBytes(buf);
            check(buf.readableBytes() == 8, "STOP is exactly 8 payload bytes");
            PacketAnnounceStop stop = new PacketAnnounceStop(); stop.fromBytes(buf);
            check(stop.sessionId == 15L, "STOP round trip");
            buf.clear(); new PacketDepartureControl(16L, false).toBytes(buf);
            check(buf.readableBytes() == 9, "Departure control is exactly 9 payload bytes");
        } finally { buf.release(); }
    }
    private static void expectInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("Malformed input accepted"); }
        catch (DecoderException expected) { checks++; }
    }

    private static PacketAnnounce start(long id) {
        PacketAnnounce packet = new PacketAnnounce(new AnnounceData("", Collections.singletonList("test:body"), ""), "A", false, 0, 0, 0);
        packet.sessionId = id; return packet;
    }
    private static PacketDepartureStart departure(long id) {
        PacketDepartureStart packet = new PacketDepartureStart(); packet.linkKey = "A"; packet.sessionId = id;
        Map<String, Integer> lengths = new HashMap<>(); lengths.put("test:m", 20); lengths.put("test:d", 5);
        packet.departure = new DepartureProgram(true);
        packet.departure.melody = "test:m";
        packet.departure.doorCloseSounds.add("test:d");
        packet.departure = packet.departure.resolve(lengths);
        return packet;
    }
    private static void delivery() throws Exception {
        ServerSessions.clear();
        FixtureWorld world = new FixtureWorld();
        TileEntityAnnouncer owner = new TileEntityAnnouncer(); owner.linkKey = "A"; world.add(owner, -10, 0, 0);
        for (int i = 0; i < 10; i++) { Speaker speaker = new Speaker(); speaker.linkKey = "A"; world.add(speaker, i, 0, 0); }
        List<Player> nearby = new ArrayList<>();
        for (int i = 0; i < 3; i++) nearby.add(player(world, i, 0, 0));
        for (int i = 0; i < 10; i++) player(world, 10000+i, 0, 0);
        RecordingDelivery out = new RecordingDelivery(); ServerSessions.delivery = out;
        PacketAnnounce routed = start(0); routed.repeatCount = 2;
        long id = ServerSessions.start(owner, routed);
        check(out.messages.size() == 3, "Ten speakers/three near/ten far: exactly three STARTs");
        Set<EntityPlayerMP> unique = new HashSet<>(out.players);
        check(unique.size() == 3 && unique.containsAll(nearby), "Overlapping recipients deduplicated");
        for (IMessage packet : out.messages) {
            check(((PacketAnnounce)packet).targets.length == 10, "Only compact target IDs in START");
            check(((PacketAnnounce)packet).repeatCount == 2, "Per-recipient START copy preserves repeat count");
        }
        out.clear();
        PacketMissingSpeakers missing = new PacketMissingSpeakers(id, new long[] {SpeakerRegistry.position(0, 0, 0), SpeakerRegistry.position(999, 0, 0)});
        ServerSessions.missing(nearby.get(0), missing);
        check(out.messages.size() == 1 && ((PacketSpeakerFallback)out.messages.get(0)).targets.size() == 1, "Fallback restricted to original session targets");
        out.clear(); ServerSessions.missing(nearby.get(0), missing);
        check(out.messages.isEmpty(), "Missing target response limited to once per session/player");
        Player outsider = (Player)world.playerEntities.get(12);
        ServerSessions.missing(outsider, missing);
        check(out.messages.isEmpty(), "Non-recipient cannot request Speaker settings");
        nearby.get(0).posX = 50000;
        out.clear(); ServerSessions.stopKey(world, "A");
        check(out.messages.size() == 3 && out.players.contains(nearby.get(0)), "STOP reaches moved recipient");
        check(((PacketAnnounceStop)out.messages.get(0)).sessionId == id, "STOP names old session");
        out.clear(); ServerSessions.stopKey(world, "A");
        check(out.messages.isEmpty(), "STOP releases session recipients");
        nearby.get(0).posX = 0;
        long first = ServerSessions.start(owner, departure(0));
        out.clear(); ServerSessions.control(first, false);
        check(out.messages.size() == 3 && !((PacketDepartureControl)out.messages.get(0)).cancel, "OFF reaches original recipients");
        long second = ServerSessions.start(owner, departure(0));
        check(first != second, "Every start gets unique ID");
        out.clear(); ServerSessions.control(first, true);
        check(out.messages.size() == 3 && ((PacketDepartureControl)out.messages.get(0)).sessionId == first, "Old CANCEL names only old session");
        out.clear(); ServerSessions.control(second, false);
        check(out.messages.size() == 3, "New session survives old CANCEL");
        ServerSessions.INSTANCE.logout(new PlayerEvent.PlayerLoggedOutEvent(nearby.get(0)));
        out.clear(); ServerSessions.control(second, false);
        check(out.messages.size() == 2, "Logout removes original recipient");
        nearby.get(1).worldObj = new FixtureWorld();
        ServerSessions.INSTANCE.changedWorld(new PlayerEvent.PlayerChangedDimensionEvent(nearby.get(1), 0, 1));
        out.clear(); ServerSessions.control(second, false);
        check(out.messages.size() == 1, "World change cleans recipient");
        ServerSessions.finished(nearby.get(2), second);
        out.clear(); ServerSessions.control(second, false);
        check(out.messages.isEmpty(), "Completion releases last recipient");
        SpeakerRegistry.clear(world);
        PacketAnnounce local = start(0); local.playLocalSound = true;
        ServerSessions.start(owner, local);
        check(out.around == 1 && out.radius == ServerSessions.LOCAL_RANGE + ServerSessions.RANGE_MARGIN, "Local-only uses bounded TargetPoint");
        ServerSessions.clear();
    }

    private static void config() throws Exception {
        FixtureWorld world = new FixtureWorld();
        Speaker speaker = new Speaker(); world.add(speaker, 0, 0, 0);
        Player player = player(world, 0, 0, 0);
        Constructor<MessageContext> ctor = MessageContext.class.getDeclaredConstructor(INetHandler.class, Side.class);
        ctor.setAccessible(true);
        MessageContext context = ctor.newInstance(player.playerNetServerHandler, Side.SERVER);
        NetworkHandler.SpeakerConfigHandler handler = new NetworkHandler.SpeakerConfigHandler();
        handler.onMessage(new PacketSpeakerConfig(0, 0, 0, "A", 32, .5F), context);
        check(speaker.linkKey.isEmpty(), "Network handler does not write TE before server tick");
        serverTick();
        check(speaker.linkKey.equals("A") && speaker.range == 32 && speaker.volume == .5F, "Server tick applies validated config");
        int dirty = speaker.dirty, updates = world.updates;
        handler.onMessage(new PacketSpeakerConfig(0, 0, 0, "A", 32, .5F), context); serverTick();
        check(speaker.dirty == dirty && updates == world.updates, "Identical GUI save has no update");
        handler.onMessage(new PacketSpeakerConfig(99, 0, 0, "ghost", 16, 1), context); serverTick();
        check(SpeakerRegistry.findByKey(world, "ghost").isEmpty(), "Invented coordinate cannot register speaker");
        for (float volume : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1F, 2F}) {
            handler.onMessage(new PacketSpeakerConfig(0, 0, 0, "bad", 16, volume), context); serverTick();
            check(speaker.linkKey.equals("A"), "Invalid C2S volume never applies");
        }
        player.posX = 100;
        handler.onMessage(new PacketSpeakerConfig(0, 0, 0, "far", 16, 1), context); serverTick();
        check(speaker.linkKey.equals("A"), "Distant edit rejected");
        player.posX = 0; player.editable = false;
        handler.onMessage(new PacketSpeakerConfig(0, 0, 0, "denied", 16, 1), context); serverTick();
        check(speaker.linkKey.equals("A"), "canPlayerEdit enforced");
        player.editable = true;
        handler.onMessage(new PacketSpeakerConfig(0, 0, 0, "other-world", 16, 1), context);
        player.worldObj = new FixtureWorld(); serverTick();
        check(speaker.linkKey.equals("A"), "World change between receipt and dispatch rejected");
        player.worldObj = world;
        TileEntityAnnouncer announcer = new TileEntityAnnouncer(); world.add(announcer, 1, 0, 0);
        new PacketConfig.Handler().onMessage(new PacketConfig(1, 0, 0, "test.js", "A", true), context);
        check(announcer.getScriptName().isEmpty(), "Ordinary config is queued"); serverTick();
        check(announcer.getScriptName().equals("test.js") && announcer.playLocalSound, "Ordinary config applied");
        TileEntityTrainTypeSelector selector = new TileEntityTrainTypeSelector(); world.add(selector, 2, 0, 0);
        new NetworkHandler.TrainTypeConfigHandler().onMessage(new PacketTrainTypeConfig(2, 0, 0,
            Collections.singletonList(new TrainTypeCondition("destination", 0)), "A", true), context);
        check(selector.conditions.isEmpty(), "Train type config is queued"); serverTick();
        check(selector.conditions.size() == 1 && selector.isControlCar, "Train type config applied");
        selector.dispatchData(Collections.singletonMap("destination", "Tokyo"));
        check("Tokyo".equals(announcer.receivedData.get("destination")), "dataMap still reaches linked announcer without TE scan");
        new NetworkHandler.TrainTypeConfigHandler().onMessage(new PacketTrainTypeConfig(2, 0, 0,
            Collections.singletonList(new TrainTypeCondition("invalid", 99)), "B", false), context); serverTick();
        check(selector.linkKey.equals("A"), "Invalid train condition type rejected");
        TileEntityStartAnnouncer start = new TileEntityStartAnnouncer(); world.add(start, 3, 0, 0);
        new NetworkHandler.StartAnnouncerConfigHandler().onMessage(new PacketStartAnnouncerConfig(3, 0, 0, "A", true), context);
        check(start.linkKey.isEmpty(), "Start config is queued"); serverTick(); check(start.isControlCar, "Start config applied");
        TileEntityStopAnnouncer stop = new TileEntityStopAnnouncer(); world.add(stop, 4, 0, 0);
        new NetworkHandler.StopAnnouncerConfigHandler().onMessage(new PacketStopAnnouncerConfig(4, 0, 0, "A", true), context);
        check(stop.linkKey.isEmpty(), "Stop config is queued"); serverTick(); check(stop.isControlCar, "Stop config applied");
        TileEntityDebugReceiver debug = new TileEntityDebugReceiver(); world.add(debug, 5, 0, 0);
        new PacketDebugConfig.Handler().onMessage(new PacketDebugConfig(5, 0, 0, " A "), context);
        check(debug.linkKey.isEmpty(), "Debug config is queued"); serverTick(); check(debug.linkKey.equals("A"), "Debug config normalized");
        TileEntityAwarenessAnnouncer awareness = new TileEntityAwarenessAnnouncer(); world.add(awareness, 6, 0, 0);
        PacketAwarenessConfig awarenessConfig = new PacketAwarenessConfig(6, 0, 0, "A", "test:a;test:b", 40, true, true, true, 10);
        new NetworkHandler.AwarenessConfigHandler().onMessage(awarenessConfig, context);
        check(awareness.soundList.isEmpty(), "Awareness config is queued"); serverTick();
        check(awareness.soundList.equals("test:a,test:b") && awareness.allowOverlap && awareness.randomOrder, "Awareness config applied");
        updates = world.updates;
        new NetworkHandler.AwarenessConfigHandler().onMessage(awarenessConfig, context); serverTick();
        check(world.updates == updates, "Normalized identical Awareness config does not reset timers or update TE");
        awarenessConfig.soundList = String.join(",", Collections.nCopies(PacketLimits.SOUNDS+1, "test:a"));
        new NetworkHandler.AwarenessConfigHandler().onMessage(awarenessConfig, context); serverTick();
        check(world.updates == updates, "Oversized Awareness list rejected");
        check(speaker.dirty == dirty, "Rejected writes do not mark dirty");
    }
    private static void serverTick() { ServerTaskQueue.INSTANCE.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START)); }

    private static void client() throws Exception {
        AnnouncePackLoader.soundTicks.put("test:body", 20);
        AnnouncePackLoader.soundTicks.put("test:m", 20);
        AnnouncePackLoader.soundTicks.put("test:d", 5);
        FixtureWorld world = new FixtureWorld(); world.isRemote = true;
        TestClient client = new TestClient(); client.world = world;
        PacketAnnounce packet = start(100); packet.targets = new long[] {SpeakerRegistry.position(0, 0, 0)};
        Thread network = new Thread(() -> client.receive(packet)); network.start(); network.join();
        check(client.worldReads == 0 && client.played.isEmpty(), "Network thread touches no world or sound");
        client.tick(); check(client.played.isEmpty(), "Missing client TE is null-safe");
        client.tick(); check(client.played.isEmpty(), "Unresolved TE is retried on client tick");
        Speaker speaker = new Speaker(); world.add(speaker, 0, 0, 0);
        syncSpeaker(speaker, "A", 16, 1.0F); client.tick();
        check(client.played.size() == 1, "Delayed TE settings recover current sound without scanning");
        client.receive(packet); client.tick();
        check(client.played.size() == 1, "Duplicate START does not replay");
        PacketAnnounce newer = start(101); newer.targets = packet.targets;
        client.receive(newer); client.receive(new PacketAnnounceStop(100)); client.tick();
        check(client.played.size() == 2 && client.live.size() == 1, "Old STOP cannot stop newer same-key session");
        client.receive(new PacketAnnounceStop(101)); client.tick();
        check(client.live.isEmpty(), "Matching STOP stops sound");
        PacketDepartureStart dep = departure(200); dep.targets = packet.targets;
        int departureFirst = client.played.size();
        client.receive(dep); client.receive(new PacketDepartureControl(200, false)); client.tick();
        check(client.played.get(departureFirst).getPositionedSoundLocation().toString().equals("test:m"), "START initializes melody before same-tick OFF");
        client.receive(new PacketDepartureControl(199, true)); client.tick();
        check(!client.ended.contains(200L), "Stale departure CANCEL ignored");
        client.receive(new PacketDepartureControl(200, true)); client.tick();
        check(client.live.isEmpty(), "Departure CANCEL stops only its channels");
        PacketDepartureStart high = departure(300); high.targets = packet.targets;
        PacketAnnounce awareness = start(301); awareness.priority = PacketAnnounce.PRIORITY_AWARENESS; awareness.targets = packet.targets;
        int before = client.played.size();
        client.receive(high); client.receive(awareness); client.tick();
        check(client.played.size() == before+1, "Awareness waits behind departure priority");
        client.receive(new PacketDepartureControl(300, true)); client.tick();
        check(client.played.size() == before+2, "Waiting Awareness resumes after departure CANCEL");
        client.receive(new PacketAnnounceStop(0)); client.tick();
        PacketDepartureStart overlapHigh = departure(400); overlapHigh.targets = packet.targets;
        PacketAnnounce overlap = start(401); overlap.priority = 0; overlap.allowOverlap = true; overlap.targets = packet.targets;
        before = client.played.size(); client.receive(overlapHigh); client.receive(overlap); client.tick();
        check(client.played.size() == before+2, "Awareness allowOverlap preserved");
        client.world = new FixtureWorld(); client.tick();
        check(client.live.isEmpty(), "World identity change clears sessions/sounds/deferred targets");
        int acknowledgements = client.ended.size();
        PacketAnnounce normal = start(500); normal.playLocalSound = true;
        normal.bodySounds = Arrays.asList("test:body", "test:body");
        client.receive(normal); client.tick();
        for (int i = 0; i < 45; i++) client.tick();
        check(client.ended.size() == acknowledgements+1 && client.ended.contains(500L), "One completion acknowledgement per session, not per sound");
        PacketAnnounce missingStart = start(600); missingStart.targets = new long[] {SpeakerRegistry.position(100, 0, 0)};
        int requests = client.missing.size(); before = client.played.size();
        client.receive(missingStart);
        for (int i = 0; i < 5; i++) client.tick();
        check(client.missing.size() == requests+1 && client.played.size() == before, "Missing TE coordinates requested once after grace period");
        PacketSpeakerFallback fallback = new PacketSpeakerFallback(600);
        fallback.targets.add(new PacketSpeakerFallback.Target(missingStart.targets[0], 64, .75F));
        client.receive(fallback); client.tick();
        check(client.played.size() == before+1, "Missing/unloaded TE recovers through exceptional compact settings response");
        for (int i = 0; i < 25; i++) client.tick();
        check(client.missing.size() == requests+1 && client.live.isEmpty(), "Fallback neither polls server nor leaves late sounds playing");
    }

    private static void ordinaryRepeats() {
        for (String sound : new String[]{"test:start", "test:one", "test:two", "test:arr"})
            AnnouncePackLoader.soundTicks.put(sound, 1);
        FixtureWorld world = new FixtureWorld(); world.isRemote = true;

        TestClient repeated = playOrdinary(world,
            new AnnounceData("test:start", Arrays.asList("test:one", "test:two"), "test:arr", 2), 700, 13);
        check(soundNames(repeated).subList(0, 7).equals(Arrays.asList("test:start", "test:one", "test:two",
            "test:start", "test:one", "test:two", "test:arr")),
            "Repeat two plays start and ordered body twice before arrMelo");

        TestClient noStart = playOrdinary(world,
            new AnnounceData(null, Arrays.asList("test:one", "test:two"), "test:arr", 2), 701, 9);
        check(soundNames(noStart).subList(0, 5).equals(Arrays.asList(
            "test:one", "test:two", "test:one", "test:two", "test:arr")),
            "Repeat works without startMelo");

        TestClient noArr = playOrdinary(world,
            new AnnounceData("test:start", Arrays.asList("test:one", "test:two"), null, 2), 702, 13);
        check(soundNames(noArr).equals(Arrays.asList("test:start", "test:one", "test:two",
            "test:start", "test:one", "test:two")) && noArr.ended.contains(702L),
            "No arrMelo completes once after all repeats");

        TestClient emptyBody = playOrdinary(world,
            new AnnounceData("test:start", Collections.<String>emptyList(), "test:arr", 2), 703, 5);
        check(soundNames(emptyBody).subList(0, 3).equals(Arrays.asList("test:start", "test:start", "test:arr")),
            "Empty body repeats startMelo before arrMelo");

        TestClient interval = new TestClient(); interval.world = world;
        interval.receive(new PacketAnnounce(new AnnounceData(null,
            Arrays.asList("test:one", "", "test:two"), Arrays.asList(0, 3, 0), null, 1),
            "A", true, 0, 0, 0));
        for (int i = 0; i < 5; i++) interval.tick();
        check(soundNames(interval).equals(Collections.singletonList("test:one")),
            "Ordinary body interval delays the following part");
        interval.tick();
        check(soundNames(interval).equals(Arrays.asList("test:one", "test:two")),
            "Ordinary body interval resumes after its tick duration");
    }

    private static TestClient playOrdinary(FixtureWorld world, AnnounceData data, long id, int ticks) {
        TestClient client = new TestClient(); client.world = world;
        PacketAnnounce packet = new PacketAnnounce(data, "A", true, 0, 0, 0); packet.sessionId = id;
        client.receive(packet);
        for (int i = 0; i < ticks; i++) client.tick();
        return client;
    }

    private static List<String> soundNames(TestClient client) {
        List<String> result = new ArrayList<>();
        for (ISound sound : client.played) result.add(sound.getPositionedSoundLocation().toString());
        return result;
    }

    private static int sessions() throws Exception {
        Field field = ServerSessions.class.getDeclaredField("SESSIONS"); field.setAccessible(true);
        return ((Map<?, ?>)field.get(null)).size();
    }
    private static MessageContext context(Player player) throws Exception {
        Constructor<MessageContext> ctor = MessageContext.class.getDeclaredConstructor(INetHandler.class, Side.class);
        ctor.setAccessible(true); return ctor.newInstance(player.playerNetServerHandler, Side.SERVER);
    }
    private static void endTick() { ServerSessions.INSTANCE.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END)); }

    private static void limitsAndExpiry() throws Exception {
        cpw.mods.fml.common.Mod mod = StationAnnounceModCore.class.getAnnotation(cpw.mods.fml.common.Mod.class);
        check("0.2.1-beta".equals(StationAnnounceModCore.VERSION) && "[0.2.1-beta]".equals(mod.acceptableRemoteVersions()), "Forge exact remote version gate");
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeLong(1).writeInt(PacketLimits.MISSING_TARGETS+1);
            expectInvalid(() -> new PacketMissingSpeakers().fromBytes(buf));
            buf.clear(); buf.writeLong(1).writeInt(PacketLimits.SESSION_TARGETS+1);
            expectInvalid(() -> new PacketSpeakerFallback().fromBytes(buf));
            PacketAnnounce bounded = start(1); bounded.targets = new long[PacketLimits.SESSION_TARGETS];
            bounded.bodySounds = Collections.nCopies(PacketLimits.BODY_SOUNDS, "test:body");
            bounded.repeatCount = PacketLimits.MAX_ANNOUNCE_REPEATS;
            buf.clear(); bounded.toBytes(buf); PacketAnnounce decoded = new PacketAnnounce(); decoded.fromBytes(buf);
            check(decoded.targets.length == PacketLimits.SESSION_TARGETS && decoded.bodySounds.size() == PacketLimits.BODY_SOUNDS
                && decoded.repeatCount == PacketLimits.MAX_ANNOUNCE_REPEATS, "START boundary round trip");
            // Forge header has a variable-length linkKey; use readHeader to find the body count.
            buf.clear(); bounded.writeHeader(buf);
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
            buf.writeInt(PacketLimits.BODY_SOUNDS+1);
            expectInvalid(() -> new PacketAnnounce().fromBytes(buf));
            for (int repeat : new int[]{0, -1, PacketLimits.MAX_ANNOUNCE_REPEATS + 1}) {
                buf.clear(); bounded.writeHeader(buf);
                cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
                cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
                buf.writeInt(0).writeInt(repeat);
                expectInvalid(() -> new PacketAnnounce().fromBytes(buf));
            }
            buf.clear(); bounded.writeHeader(buf);
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
            buf.writeInt(0);
            PacketAnnounce legacyDecoded = new PacketAnnounce(); legacyDecoded.fromBytes(buf);
            check(legacyDecoded.repeatCount == 1, "Legacy START payload defaults repeat count to one");
            buf.writeByte(1);
            expectInvalid(() -> new PacketAnnounce().fromBytes(buf.readerIndex(0)));
            buf.clear(); bounded.targets = new long[0]; bounded.writeHeader(buf);
            buf.setInt(buf.writerIndex()-4, PacketLimits.SESSION_TARGETS+1);
            expectInvalid(() -> new PacketAnnounce().fromBytes(buf));
            PacketMissingSpeakers maxMissing = new PacketMissingSpeakers(1, new long[PacketLimits.MISSING_TARGETS]);
            buf.clear(); maxMissing.toBytes(buf); check(buf.readableBytes() == 4108, "Missing payload capped at 4108 bytes");
            PacketSpeakerFallback maxFallback = new PacketSpeakerFallback(1);
            for (int i = 0; i < PacketLimits.SESSION_TARGETS; i++) maxFallback.targets.add(new PacketSpeakerFallback.Target(i, 16, 1));
            buf.clear(); maxFallback.toBytes(buf); check(buf.readableBytes() == 8204, "Fallback payload capped at 8204 bytes");
        } finally { buf.release(); }

        ServerSessions.clear(); ServerTaskQueue.INSTANCE.clear();
        FixtureWorld world = new FixtureWorld(); TileEntityAnnouncer owner = new TileEntityAnnouncer();
        owner.linkKey = "A"; world.add(owner, -10, 0, 0);
        for (int i = 0; i < 10; i++) { Speaker s = new Speaker(); s.linkKey = "A"; world.add(s, i, 0, 0); }
        Player player = player(world, 0, 0, 0); RecordingDelivery out = new RecordingDelivery(); ServerSessions.delivery = out;
        long id = ServerSessions.start(owner, start(0)); out.clear();
        ServerSessions.missing(player, new PacketMissingSpeakers(id, new long[11]));
        check(out.messages.isEmpty(), "11 requests for 10 allowed targets rejected before set allocation");
        ServerSessions.missing(player, new PacketMissingSpeakers(id, new long[] {SpeakerRegistry.position(0, 0, 0)}));
        check(out.messages.isEmpty(), "Oversized attempt consumes single fallback request");
        new NetworkHandler.FinishedHandler().onMessage(new PacketSessionFinished(id), context(player));
        check(sessions() == 1, "FINISHED remains queued"); serverTick(); check(sessions() == 0, "FINISHED handler cleans recipient/session");

        long expired = ServerSessions.start(owner, start(0));
        endTick(); long surviving = ServerSessions.start(owner, departure(0));
        ServerSessions.expireSessions(ServerSessions.SESSION_TTL_TICKS-1);
        check(sessions() == 2, "No early TTL expiration");
        out.clear();
        Field clock = ServerSessions.class.getDeclaredField("serverTick"); clock.setAccessible(true);
        clock.setLong(null, ServerSessions.SESSION_TTL_TICKS-1); endTick();
        check(sessions() == 1 && out.messages.size() == 1 && ((PacketAnnounceStop)out.messages.get(0)).sessionId == expired,
            "Periodic sweep stops only expired session when ACK is absent");
        ServerSessions.control(surviving, false);
        ServerSessions.expireSessions(ServerSessions.SESSION_TTL_TICKS+1);
        check(sessions() == 1, "OFF extends TTL for remaining chorus/door close");
        ServerSessions.expireSessions(2*ServerSessions.SESSION_TTL_TICKS);
        check(sessions() == 0, "Refreshed TTL still expires without ACK");

        ServerSessions.clear(); id = ServerSessions.start(owner, start(0));
        final int[] ran = {0};
        for (int i = 0; i < ServerTaskQueue.MAX_PENDING+50; i++) ServerTaskQueue.INSTANCE.enqueue(() -> ran[0]++);
        new NetworkHandler.FinishedHandler().onMessage(new PacketSessionFinished(id), context(player));
        serverTick(); check(ran[0] == ServerTaskQueue.MAX_PER_TICK && sessions() == 1, "Overflow drops ACK and bounds work per tick");
        for (int i = 0; i < 5; i++) serverTick();
        check(ran[0] == ServerTaskQueue.MAX_PENDING && sessions() == 1, "Overflow tasks are not retained indefinitely");
        ServerSessions.expireSessions(ServerSessions.SESSION_TTL_TICKS);
        check(sessions() == 0, "TTL guarantees cleanup after queue-dropped ACK");

        ServerSessions.start(owner, start(0)); out.clear();
        ServerSessions.INSTANCE.respawn(new PlayerEvent.PlayerRespawnEvent(player));
        check(sessions() == 0 && out.messages.get(0) instanceof PacketAnnounceStop, "Respawn stops and releases old session");
        ServerSessions.start(owner, start(0)); owner.onChunkUnload(); check(sessions() == 0, "Owner unload cleans sessions"); owner.validate();
        ServerSessions.start(owner, start(0)); ServerSessions.INSTANCE.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); check(sessions() == 0, "Logout cleans session");
        ServerSessions.start(owner, start(0)); ServerSessions.INSTANCE.changedWorld(new PlayerEvent.PlayerChangedDimensionEvent(player, 0, 1)); check(sessions() == 0, "Dimension change cleans session");
        ServerSessions.start(owner, start(0)); ServerSessions.INSTANCE.unload(new net.minecraftforge.event.world.WorldEvent.Unload(world));
        check(sessions() == 0 && SpeakerRegistry.findByKey(world, "A").isEmpty(), "World unload clears sessions and speakers");
        PacketAnnounce local = start(0); local.playLocalSound = true;
        ServerSessions.start(owner, local); ServerSessions.stopAll(); check(sessions() == 0, "Global stop clears sessions");
        ServerSessions.start(owner, local); ServerTaskQueue.INSTANCE.enqueue(() -> ran[0]++);
        new StationAnnounceModCore().serverStopped(null); int before = ran[0]; serverTick();
        check(sessions() == 0 && ran[0] == before, "Server stop clears sessions and pending tasks");

        // The sending side must never generate a packet rejected by its own decoder.
        for (int i = 0; i < PacketLimits.SESSION_TARGETS+10; i++) {
            Speaker s = new Speaker(); s.linkKey = "A"; s.range = 128;
            world.add(s, i%20, i/20, 0);
        }
        out.clear(); ServerSessions.start(owner, start(0));
        check(out.messages.size() == 1 && ((PacketAnnounce)out.messages.get(0)).targets.length == PacketLimits.SESSION_TARGETS, "START sender caps dense recipient at 512 targets");
        PacketAnnounce tooMany = start(0); tooMany.bodySounds = Collections.nCopies(PacketLimits.BODY_SOUNDS+1, "test:body");
        int count = sessions(); out.clear();
        check(ServerSessions.start(owner, tooMany) == 0 && out.messages.isEmpty() && sessions() == count, "Oversized script sequence rejected before session allocation");
        PacketAnnounce invalidRepeat = start(0); invalidRepeat.repeatCount = 0;
        check(ServerSessions.start(owner, invalidRepeat) == 0 && sessions() == count,
            "Invalid direct repeat count is rejected before session allocation");
        ServerSessions.clear(); SpeakerRegistry.clear(world); LoadedSamTiles.clear(world);
    }

    private static void fallbackAuthority() {
        for (String currentKey : new String[] {"A", "B", ""}) {
            FixtureWorld world = new FixtureWorld(); world.isRemote = true;
            TestClient client = new TestClient(); client.world = world;
            PacketAnnounce packet = start(900); packet.targets = new long[] {SpeakerRegistry.position(0, 0, 0)};
            packet.bodySounds = Arrays.asList("test:body", "test:body", "test:body");
            Speaker formal = new Speaker(); world.add(formal, 0, 0, 0);
            check(!formal.isClientConfigSynced() && formal.linkKey.isEmpty()
                && formal.range == 16 && formal.volume == 1.0F, "New client Speaker TE is not configuration-synchronized");
            client.receive(packet); for (int i = 0; i < 5; i++) client.tick();
            check(client.played.isEmpty() && client.missing.size() == 1,
                "Unsynchronized existing Speaker TE remains deferred and requests fallback");
            PacketSpeakerFallback fallback = new PacketSpeakerFallback(900);
            fallback.targets.add(new PacketSpeakerFallback.Target(packet.targets[0], 64, .75F));
            client.receive(fallback); client.tick();
            check(client.played.size() == 1 && client.played.get(0).getVolume() == 2.0F,
                "Unsynchronized existing Speaker TE uses fallback for the current sound");
            syncSpeaker(formal, currentKey, 16, .25F);
            check(formal.isClientConfigSynced(), "S35 application marks client Speaker configuration synchronized");
            for (int i = 0; i < 20; i++) client.tick();
            if (currentKey.equals("A")) {
                check(client.played.size() == 2 && client.played.get(1).getVolume() == .25F,
                    "Synchronized TE range/volume override fallback");
            } else check(client.played.size() == 1,
                "Synchronized mismatched/empty TE key suppresses stale fallback");
            world.tiles.clear(); // A resolved TE must also have evicted its old fallback entry.
            int played = client.played.size(); for (int i = 0; i < 21; i++) client.tick();
            check(client.played.size() == played, "Synchronized TE evicts fallback even after later TE unload");
            client.receive(new PacketAnnounceStop(900)); client.tick();
            client.receive(fallback); client.tick();
            check(client.played.size() == played && client.live.isEmpty(), "Late fallback after session stop is ignored");
        }

        Speaker diskLoaded = new Speaker();
        NBTTagCompound saved = new NBTTagCompound();
        Speaker savedSource = new Speaker(); savedSource.linkKey = "A"; savedSource.writeToNBT(saved);
        diskLoaded.readFromNBT(saved);
        check(!diskLoaded.isClientConfigSynced(), "Ordinary readFromNBT does not imply a server description was received");
    }

    private static void syncSpeaker(Speaker client, String key, int range, float volume) {
        Speaker server = new Speaker(); server.xCoord = client.xCoord; server.yCoord = client.yCoord; server.zCoord = client.zCoord;
        server.linkKey = key; server.range = range; server.volume = volume;
        client.onDataPacket(null, (net.minecraft.network.play.server.S35PacketUpdateTileEntity)server.getDescriptionPacket());
    }

    private static class Speaker extends TileEntitySpeaker { int dirty; @Override public void markDirty() { dirty++; } }
    private static class FixtureWorld extends World {
        int updates;
        final Map<Long, TileEntity> tiles = new HashMap<>();
        FixtureWorld() {
            super(new SaveHandlerMP(), "network-test", new WorldProviderSurface(), new WorldSettings(0, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT), new Profiler());
            loadedTileEntityList = new ArrayList() { @Override public Iterator iterator() { throw new AssertionError("Full TE scan"); } };
        }
        void add(TileEntity tile, int x, int y, int z) {
            tile.setWorldObj(this); tile.xCoord = x; tile.yCoord = y; tile.zCoord = z;
            tiles.put(SpeakerRegistry.position(x, y, z), tile); tile.validate();
        }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
        @Override public TileEntity getTileEntity(int x, int y, int z) { return tiles.get(SpeakerRegistry.position(x, y, z)); }
        @Override public void markBlockForUpdate(int x, int y, int z) { updates++; }
        @Override public int getBlockMetadata(int x, int y, int z) { return 0; }
        @Override public net.minecraft.block.Block getBlock(int x, int y, int z) { return net.minecraft.init.Blocks.air; }
        @Override public void markTileEntityChunkModified(int x, int y, int z, TileEntity tile) {}
        @Override public boolean canMineBlock(EntityPlayer player, int x, int y, int z) { return true; }
    }
    private static class Player extends EntityPlayerMP {
        UUID uuid; boolean editable;
        Player() { super(null, null, new GameProfile(UUID.randomUUID(), "test"), null); }
        @Override public UUID getUniqueID() { return uuid; }
        @Override public boolean canPlayerEdit(int x, int y, int z, int side, ItemStack stack) { return editable; }
        @Override public ItemStack getHeldItem() { return null; }
    }
    private static Player player(World world, double x, double y, double z) throws Exception {
        // Same headless fixture technique as SwitchModelTest: avoid MinecraftServer/crafting boot.
        Constructor<?> constructor = sun.reflect.ReflectionFactory.getReflectionFactory()
            .newConstructorForSerialization(Player.class, Object.class.getDeclaredConstructor());
        Player player = (Player)constructor.newInstance();
        player.setEntityId(++playerId);
        player.uuid = UUID.randomUUID(); player.editable = true; player.worldObj = world;
        player.posX = x; player.posY = y; player.posZ = z;
        new NetHandlerPlayServer(null, new NetworkManager(false) { @Override public boolean isChannelOpen() { return true; } }, player);
        world.playerEntities.add(player); return player;
    }
    private static class RecordingDelivery implements ServerSessions.Delivery {
        final List<IMessage> messages = new ArrayList<>(); final List<EntityPlayerMP> players = new ArrayList<>();
        int around; double radius;
        public void send(IMessage packet, EntityPlayerMP player) { messages.add(packet); players.add(player); }
        public void around(IMessage packet, TargetPoint point) { around++; radius = point.range; }
        public void all(IMessage packet) { messages.add(packet); }
        void clear() { messages.clear(); players.clear(); }
    }
    private static class TestClient extends AnnounceManager {
        World world; int worldReads;
        final List<ISound> played = new ArrayList<>(); final Set<ISound> live = new HashSet<>(); final Set<Long> ended = new HashSet<>();
        @Override protected World currentWorld() { worldReads++; return world; }
        @Override protected void playSound(ISound sound) { played.add(sound); live.add(sound); }
        @Override protected void stopSound(ISound sound) { live.remove(sound); }
        @Override protected boolean inSpeakerRange(TileEntitySpeaker speaker) { return true; }
        @Override protected boolean inRange(int x, int y, int z, int range) { return true; }
        @Override protected void requestMissing(PacketMissingSpeakers packet) { missing.add(packet); }
        final List<PacketMissingSpeakers> missing = new ArrayList<>();
        @Override protected void finished(long id) { check(ended.add(id), "No duplicate completion acknowledgement"); }
        void tick() { onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START)); }
    }
}
