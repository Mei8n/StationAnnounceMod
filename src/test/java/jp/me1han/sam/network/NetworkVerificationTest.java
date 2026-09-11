package jp.me1han.sam.network;

import java.lang.reflect.*;
import java.util.*;
import javax.script.ScriptEngine;
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
import jp.me1han.sam.client.ClientSpeakerRegistry;
import jp.me1han.sam.compat.TrainCompat;
import jp.me1han.sam.compat.TrainCompatRegistry;
import jp.me1han.sam.compat.TrainDetectionManager;
import jp.me1han.sam.compat.TrainSnapshot;
import jp.me1han.sam.render.*;
import jp.me1han.sam.link.LinkKey;
import jp.me1han.sam.link.SamLinkRegistry;
import jp.me1han.sam.trigger.*;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
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
        AnnouncePackLoader.soundTicks.put("test:body", 20);
        AnnouncePackLoader.soundTicks.put("test:a", 20);
        lifecycle(); nashornBeanProperty(); linkRouting(); deterministicSpeakerRouting(); trainCompat(); wireBounds(); senderReceiverValidation(); delivery(); canonicalOrdinaryTiming(); departureInterval(); config(); client(); ordinaryRepeats(); dynamicRouting(); initialRouteGate(); serverDescriptorRouting(); coalescedRouteUpdates(); serverTaskQueueFairness(); limitsAndExpiry(); fallbackAuthority();
        SpeakerRegistry.clear(); ClientSpeakerRegistry.clear(); SamLinkRegistry.clear(); LoadedSamTiles.clear(); ServerSessions.clear();
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

    private static void deterministicSpeakerRouting() throws Exception {
        List<int[]> ascending = new ArrayList<>();
        for (int i = 0; i < PacketLimits.SESSION_TARGETS + 1; i++)
            ascending.add(new int[] {i - 256, i % 7, 512 - i});
        List<int[]> descending = new ArrayList<>(ascending); Collections.reverse(descending);
        List<int[]> random = new ArrayList<>(ascending); Collections.shuffle(random, new Random(987654321L));

        List<String> expected = routingSignatureFor(ascending, true);
        check(expected.equals(routingSignatureFor(descending, false)),
            "Descending Speaker registration preserves deterministic route chunks");
        check(expected.equals(routingSignatureFor(random, false)),
            "Random Speaker registration preserves deterministic route chunks");
    }

    private static List<String> routingSignatureFor(List<int[]> coordinates, boolean verifyReload) throws Exception {
        ServerSessions.clear(); SpeakerRegistry.clear();
        FixtureWorld world = new FixtureWorld();
        TileEntityAnnouncer owner = new TileEntityAnnouncer(); owner.setLinkKey("A"); world.add(owner, -1000, 0, 0);
        player(world, 0, 0, 0);
        List<Speaker> speakers = new ArrayList<>();
        for (int[] coordinate : coordinates) {
            Speaker speaker = new Speaker(); speaker.linkKey = "A";
            world.add(speaker, coordinate[0], coordinate[1], coordinate[2]); speakers.add(speaker);
        }
        RecordingDelivery out = new RecordingDelivery(); ServerSessions.delivery = out;
        long sessionId = ServerSessions.start(owner, start(0));
        List<String> signature = routingSignature(out, sessionId);
        check(signature.size() == PacketLimits.SESSION_TARGETS + 3,
            "513 deterministic targets retain two chunk headers and every Speaker");
        check(signature.get(0).equals("0/2") && signature.get(PacketLimits.SESSION_TARGETS + 1).equals("1/2"),
            "Deterministic 513-Speaker boundary is 512 targets followed by one target");

        if (verifyReload) {
            for (Speaker speaker : speakers) speaker.onChunkUnload();
            List<int[]> reloadOrder = new ArrayList<>(coordinates); Collections.shuffle(reloadOrder, new Random(1234L));
            for (int[] coordinate : reloadOrder) {
                Speaker speaker = new Speaker(); speaker.linkKey = "A";
                world.add(speaker, coordinate[0], coordinate[1], coordinate[2]);
            }
            out.clear();
            long reloadedSession = ServerSessions.start(owner, start(0));
            check(signature.equals(routingSignature(out, reloadedSession)),
                "Speaker unload and shuffled reload preserve identical route chunks");
        }
        ServerSessions.clear(); SpeakerRegistry.clear();
        return signature;
    }

    private static List<String> routingSignature(RecordingDelivery out, long sessionId) {
        List<String> signature = new ArrayList<>();
        for (IMessage message : out.messages) {
            if (!(message instanceof PacketSessionSpeakerRoutes)) continue;
            PacketSessionSpeakerRoutes route = (PacketSessionSpeakerRoutes)message;
            if (route.sessionId != sessionId) continue;
            signature.add(route.chunkIndex + "/" + route.chunkCount);
            for (PacketSessionSpeakerRoutes.Target target : route.targets)
                signature.add(target.position + ":" + target.range + ":" + target.volume);
        }
        return signature;
    }

    private static void trainCompat() throws Exception {
        Field activeField = TrainCompatRegistry.class.getDeclaredField("active");
        activeField.setAccessible(true);
        TrainCompat original = (TrainCompat)activeField.get(null);

        try {
            TrainDetectionManager.clear();
            check(!TrainCompatRegistry.get().isAvailable(), "RTM-free registry starts with unavailable no-op compat");
            FixtureWorld noRtmWorld = new FixtureWorld();
            TileEntityStartAnnouncer noRtmStart = new TileEntityStartAnnouncer();
            TileEntityStopAnnouncer noRtmStop = new TileEntityStopAnnouncer();
            TileEntityTrainTypeSelector noRtmSelector = new TileEntityTrainTypeSelector();
            noRtmWorld.add(noRtmStart, 0, 0, 0);
            noRtmWorld.add(noRtmStop, 1, 0, 0);
            noRtmWorld.add(noRtmSelector, 2, 0, 0);
            noRtmStart.updateEntity();
            noRtmStop.updateEntity();
            noRtmSelector.updateEntity();
            check(!TrainCompatRegistry.get().isAvailable()
                    && TrainDetectionManager.findFirstTrain(noRtmWorld,
                        AxisAlignedBB.getBoundingBox(-2, -2, -2, 2, 2, 2), false) == null,
                "RTM-free train tile updates and indexed queries remain safely empty");

            FakeTrainCompat fake = new FakeTrainCompat();
            activeField.set(null, fake);
            TrainDetectionManager.clear();

            FixtureWorld indexed = new FixtureWorld();
            FakeTrain near = indexed.addTrain(10, false, 100L, "near", 0, 0);
            FakeTrain control = indexed.addTrain(20, true, 200L, "control", 2, 0);
            indexed.addTrain(30, true, 300L, "far", 100, 100);
            AxisAlignedBB local = AxisAlignedBB.getBoundingBox(-2, -2, -2, 4, 3, 3);
            for (int i = 0; i < 100; i++)
                check(TrainDetectionManager.findFirstTrain(indexed, local, false) == near,
                    "Repeated train query keeps deterministic nearest result");
            check(indexed.entityScans == 1 && fake.wraps == 3,
                "One World and tick performs one loaded-entity enumeration and wrap pass");
            indexed.nextTick();
            check(indexed.entityScans == 1, "A tick without train queries performs no eager rebuild");
            TrainDetectionManager.findFirstTrain(indexed, local, false);
            check(indexed.entityScans == 2 && fake.wraps == 6,
                "First query in the next tick rebuilds the train index exactly once");
            check(TrainDetectionManager.findFirstTrain(indexed, local, true) == control,
                "Control-car query excludes a nearer non-control train");

            FixtureWorld boundaryWorld = new FixtureWorld();
            FakeTrain crossing = boundaryWorld.addTrain(40, true, 400L, "crossing", 16, 0);
            crossing.boundingBox.setBounds(15.5, 0, -.5, 16.5, 1, .5);
            TrainSnapshot crossingResult = TrainDetectionManager.findFirstTrain(boundaryWorld,
                AxisAlignedBB.getBoundingBox(16.25, 0, -.25, 16.75, 1, .25), true);
            check(crossingResult == crossing && crossing.controlChecks == 1,
                "Cell-crossing train is found and deduplicated across indexed cells");
            check(TrainDetectionManager.findFirstTrain(boundaryWorld,
                AxisAlignedBB.getBoundingBox(80, 0, 80, 81, 1, 81), false) == null,
                "Spatial query excludes distant trains");
            check(TrainDetectionManager.findFirstTrain(boundaryWorld,
                AxisAlignedBB.getBoundingBox(17, 0, -.25, 17.5, 1, .25), false) == null,
                "Shared spatial cell does not bypass final train/query AABB intersection");

            FixtureWorld clientWorld = new FixtureWorld(); clientWorld.isRemote = true;
            clientWorld.addTrain(45, true, 450L, "client", 0, 0);
            int cachedBeforeClient = trainDetectionWorlds();
            check(TrainDetectionManager.findFirstTrain(clientWorld, local, false) == null
                    && clientWorld.entityScans == 0 && trainDetectionWorlds() == cachedBeforeClient,
                "Client World never builds a train index");

            FixtureWorld deterministicWorld = new FixtureWorld();
            deterministicWorld.addTrain(60, false, 600L, "higher-id", 0, 0);
            FakeTrain lowerId = deterministicWorld.addTrain(50, false, 500L, "lower-id", 0, 0);
            check(TrainDetectionManager.findFirstTrain(deterministicWorld, local, false) == lowerId,
                "Equal-distance train selection uses entity id as deterministic tie break");
            check(indexed.entityScans == 2,
                "A query in another World does not rebuild the first World index");

            int cached = trainDetectionWorlds();
            TrainDetectionManager.INSTANCE.unload(new net.minecraftforge.event.world.WorldEvent.Unload(boundaryWorld));
            check(trainDetectionWorlds() == cached - 1, "World unload releases its train index and Entity references");
            int boundaryScans = boundaryWorld.entityScans;
            TrainDetectionManager.findFirstTrain(boundaryWorld, local, false);
            check(boundaryWorld.entityScans == boundaryScans + 1,
                "A query after unload creates a fresh World index");
            TrainDetectionManager.clear();
            check(trainDetectionWorlds() == 0, "Server-stop style clear releases every train index");

            FixtureWorld offWorld = new FixtureWorld();
            FakeTrain nonControl = offWorld.addTrain(10, false, 100L, "non-control", 1, 0);
            offWorld.addTrain(20, true, 200L, "control", 2, 0);
            CountingAnnouncer offReceiver = new CountingAnnouncer(); offReceiver.setLinkKey("selector-off");
            offWorld.add(offReceiver, 0, 0, 0);
            TileEntityTrainTypeSelector off = new TileEntityTrainTypeSelector(); off.setLinkKey("selector-off");
            off.conditions.add(new TrainTypeCondition("name", 0)); offWorld.add(off, 1, 0, 0);
            off.updateEntity();
            check("non-control".equals(offReceiver.receivedData.get("name")) && offReceiver.dataReceives == 1,
                "Selector without control-car filtering dispatches selected train data");
            off.updateEntity();
            check(offReceiver.dataReceives == 1, "Selector does not resend while the same entity remains selected");
            offWorld.loadedEntityList.clear(); offWorld.nextTick();
            off.updateEntity();
            check(lastTrainId(off) == -1, "Selector clears entity identity after all trains leave");
            offWorld.loadedEntityList.add(nonControl); offWorld.nextTick();
            off.updateEntity();
            check(offReceiver.dataReceives == 2, "Selector sends again when the same entity re-enters");

            FixtureWorld onWorld = new FixtureWorld();
            onWorld.addTrain(11, false, 110L, "non-control", 1, 0);
            FakeTrain onControl = onWorld.addTrain(21, true, 210L, "control", 2, 0);
            CountingAnnouncer onReceiver = new CountingAnnouncer(); onReceiver.setLinkKey("selector-on");
            onWorld.add(onReceiver, 0, 0, 0);
            TileEntityTrainTypeSelector on = new TileEntityTrainTypeSelector(); on.setLinkKey("selector-on");
            on.isControlCar = true; on.conditions.add(new TrainTypeCondition("name", 0)); onWorld.add(on, 1, 0, 0);
            on.updateEntity();
            check("control".equals(onReceiver.receivedData.get("name"))
                && lastTrainId(on) == onControl.getEntityId(),
                "Control-car selector skips a nearer non-control car");
            int receives = onReceiver.dataReceives;
            onWorld.loadedEntityList.remove(onControl); onWorld.nextTick();
            on.updateEntity();
            check(onReceiver.dataReceives == receives && lastTrainId(on) == -1,
                "A non-control car neither sends data nor becomes the remembered selector entity");
            onWorld.loadedEntityList.add(onControl); onWorld.nextTick();
            on.updateEntity();
            check(onReceiver.dataReceives == receives + 1,
                "Control car can be processed again after no qualifying train was present");

            FixtureWorld triggerWorld = new FixtureWorld();
            FakeTrain triggerTrain = triggerWorld.addTrain(70, true, 700L, "trigger", 2, 0);
            CountingAnnouncer triggerReceiver = new CountingAnnouncer(); triggerReceiver.setLinkKey("train-trigger");
            triggerWorld.add(triggerReceiver, 0, 0, 0);
            TileEntityStartAnnouncer start = new TileEntityStartAnnouncer(); start.setLinkKey("train-trigger");
            triggerWorld.add(start, 1, 0, 0);
            TileEntityStopAnnouncer stop = new TileEntityStopAnnouncer(); stop.setLinkKey("train-trigger");
            stop.isControlCar = true; triggerWorld.add(stop, 2, 0, 0);
            start.updateEntity();
            stop.updateEntity();
            check(triggerReceiver.starts == 1 && triggerReceiver.stops == 1 && triggerWorld.entityScans == 1,
                "Start/stop share one indexed scan while preserving formation triggers");
            check(triggerTrain.formationChecks == 1,
                "Formation id reflection result is cached across detectors in one tick");
            start.updateEntity();
            stop.updateEntity();
            check(triggerReceiver.starts == 1 && triggerReceiver.stops == 1,
                "Start/stop trigger only once while the same formation remains");
            triggerWorld.loadedEntityList.clear();
            triggerWorld.addTrain(71, true, 700L, "same-formation", 2, 0); triggerWorld.nextTick();
            start.updateEntity();
            stop.updateEntity();
            check(triggerReceiver.starts == 1 && triggerReceiver.stops == 1,
                "A different entity from the same formation does not cause a duplicate trigger");
            triggerWorld.loadedEntityList.clear(); triggerWorld.nextTick();
            start.updateEntity();
            stop.updateEntity();
            triggerWorld.addTrain(72, true, 700L, "reentered", 2, 0); triggerWorld.nextTick();
            start.updateEntity();
            stop.updateEntity();
            check(triggerReceiver.starts == 2 && triggerReceiver.stops == 2,
                "Start/stop trigger again after the formation leaves and re-enters");
        } finally {
            activeField.set(null, original);
            TrainDetectionManager.clear();
            SamLinkRegistry.clear();
        }
    }

    private static int trainDetectionWorlds() throws Exception {
        Field field = TrainDetectionManager.class.getDeclaredField("WORLDS");
        field.setAccessible(true);
        return ((Map<?, ?>)field.get(null)).size();
    }

    private static int clientSessionCount(AnnounceManager manager) throws Exception {
        Field field = AnnounceManager.class.getDeclaredField("activeSessions");
        field.setAccessible(true);
        return ((Map<?, ?>)field.get(manager)).size();
    }

    private static int lastTrainId(TileEntityTrainTypeSelector selector) throws Exception {
        Field field = TileEntityTrainTypeSelector.class.getDeclaredField("lastTrainId");
        field.setAccessible(true);
        return field.getInt(selector);
    }

    private static void nashornBeanProperty() throws Exception {
        SamLinkRegistry.clear();
        ScriptEngine engine = SamScriptEngineFactory.requireEngine();
        FixtureWorld world = new FixtureWorld();
        TileEntityAnnouncer tile = new TileEntityAnnouncer(); tile.setLinkKey("A"); world.add(tile, 1, 0, 0);
        tile.receivedData.put("name", "snapshot");
        engine.put("tile", jp.me1han.sam.script.AnnounceScriptContext.snapshot(tile));
        Object result = engine.eval("String(tile.linkKey) + ':' + String(tile.getLinkKey()) + ':'"
            + " + String(tile.receivedData.get('name'));");
        check("A:A:snapshot".equals(String.valueOf(result)),
            "Nashorn maps documented context getters to JavaBeans properties");
        engine.eval("try { tile.linkKey = 'B'; tile.receivedData.put('name', 'changed'); } catch (expected) {}");
        check("A".equals(tile.getLinkKey()) && "snapshot".equals(tile.receivedData.get("name"))
            && SamLinkRegistry.findFirst(world, "A", TileEntityAnnouncer.class) == tile,
            "Script context writes cannot mutate or reindex the real announcer");

        TileEntityDepartureMelody melody = new TileEntityDepartureMelody(); melody.setLinkKey("A"); world.add(melody, 2, 0, 0);
        engine.put("tile", jp.me1han.sam.script.DepartureScriptContext.snapshot(melody));
        result = engine.eval("String(tile.linkKey) + ':' + (typeof tile.receivedData);");
        check("A:undefined".equals(String.valueOf(result)),
            "Departure context exposes linkKey without ordinary receivedData");

        NBTTagCompound saved = new NBTTagCompound(); tile.writeToNBT(saved);
        TileEntityAnnouncer loaded = new TileEntityAnnouncer(); loaded.readFromNBT(saved);
        check("A".equals(saved.getString("linkKey")) && "A".equals(loaded.getLinkKey()),
            "Private JavaBeans property retains the existing linkKey NBT format");
        SamLinkRegistry.clear(world);
    }

    private static void linkRouting() {
        SamLinkRegistry.clear();
        check(LinkKey.isEmpty(null) && LinkKey.isEmpty("") && LinkKey.isEmpty("   "), "Null and blank link keys are empty");
        check(LinkKey.equals(" test ", "test"), "Link keys ignore surrounding whitespace");
        check(!LinkKey.equals("Test", "test"), "Link keys remain case-sensitive");

        FixtureWorld world = new FixtureWorld();
        CountingAnnouncer first = new CountingAnnouncer(); first.setLinkKey(" A "); world.add(first, 1, 0, 0);
        CountingAnnouncer second = new CountingAnnouncer(); second.setLinkKey("A"); world.add(second, 2, 0, 0);
        check(SamLinkRegistry.findAll(world, "A", TileEntityAnnouncer.class).size() == 2, "Validate registers multiple devices per key");
        check(SamLinkRegistry.findFirst(world, " A ", TileEntityAnnouncer.class) == first, "findFirst preserves registration order");
        check(SamLinkRegistry.findAll(world, "A", TileEntityAnnouncer.class).get(0) == first
            && SamLinkRegistry.findAll(world, "A", TileEntityAnnouncer.class).get(1) == second,
            "findAll initially follows stable registration order");

        FixtureWorld another = new FixtureWorld();
        CountingAnnouncer isolated = new CountingAnnouncer(); isolated.setLinkKey("A"); another.add(isolated, 1, 0, 0);
        check(SamLinkRegistry.findAll(another, "A", TileEntityAnnouncer.class).size() == 1, "World identity isolates logical links");

        first.setLinkKey("B");
        check(SamLinkRegistry.findAll(world, "A", TileEntityAnnouncer.class).size() == 1
            && SamLinkRegistry.findFirst(world, "B", TileEntityAnnouncer.class) == first, "A to B reindex is immediate");
        first.setLinkKey("A");
        check(SamLinkRegistry.findFirst(world, "A", TileEntityAnnouncer.class) == first,
            "A to B to A reindex preserves findFirst priority");
        List<TileEntityAnnouncer> stable = SamLinkRegistry.findAll(world, "A", TileEntityAnnouncer.class);
        check(stable.get(0) == first && stable.get(1) == second,
            "A to B to A reindex preserves findAll order");
        first.setLinkKey("   ");
        check(SamLinkRegistry.findFirst(world, "A", TileEntityAnnouncer.class) == second, "Empty key removes registry membership");
        first.setLinkKey("A");
        stable = SamLinkRegistry.findAll(world, "A", TileEntityAnnouncer.class);
        check(stable.get(0) == first && stable.get(1) == second, "Empty to A reindex retains stable order");

        TileEntityStartAnnouncer start = new TileEntityStartAnnouncer(); start.setLinkKey("A"); world.add(start, 3, 0, 0);
        start.onRedstoneUpdate(true);
        check(first.starts == 1 && second.starts == 0, "START routes only to the stable first announcer");

        TileEntityStopAnnouncer stop = new TileEntityStopAnnouncer(); stop.setLinkKey("A"); world.add(stop, 4, 0, 0);
        stop.onRedstoneUpdate(true);
        check(first.stops == 1 && second.stops == 0, "STOP performs key-level stop once");

        CountingAwareness awareness1 = new CountingAwareness(); awareness1.setLinkKey("A"); awareness1.playAfterDeparture = true;
        world.add(awareness1, 5, 0, 0);
        CountingAwareness awareness2 = new CountingAwareness(); awareness2.setLinkKey(" A "); awareness2.playAfterDeparture = true;
        world.add(awareness2, 6, 0, 0);
        second.notifyDepartureMelodyFinished();
        check(awareness1.scheduled == 1 && awareness2.scheduled == 1, "DEPARTURE_FINISHED reaches every awareness device");

        TileEntityTrainTypeSelector selector = new TileEntityTrainTypeSelector(); selector.setLinkKey("A"); world.add(selector, 7, 0, 0);
        Map<String, String> data = new HashMap<>(); data.put("type", "rapid"); selector.dispatchData(data);
        check("rapid".equals(first.receivedData.get("type")) && "rapid".equals(second.receivedData.get("type")),
            "Train selector data routes to every linked announcer");

        SamTrigger metadata = new SamTrigger(SamTriggerType.ANNOUNCE_START, " A ", 8, 9, 10, SamTriggerSourceType.TRAIN, 42L);
        check(metadata.linkKey.equals("A") && metadata.formationId == 42L && metadata.sourceX == 8,
            "Trigger preserves normalized key, position, source type and formation ID");

        FixtureWorld orderWorld = new FixtureWorld();
        CountingAnnouncer orderFirst = new CountingAnnouncer(); orderFirst.setLinkKey("A"); orderWorld.add(orderFirst, 1, 0, 0);
        CountingAnnouncer orderSecond = new CountingAnnouncer(); orderSecond.setLinkKey("A"); orderWorld.add(orderSecond, 2, 0, 0);
        orderFirst.setLinkKey("B"); orderFirst.setLinkKey("A"); orderFirst.invalidate();
        CountingAnnouncer orderThird = new CountingAnnouncer(); orderThird.setLinkKey("A"); orderWorld.add(orderThird, 3, 0, 0);
        List<TileEntityAnnouncer> afterReload = SamLinkRegistry.findAll(orderWorld, "A", TileEntityAnnouncer.class);
        check(afterReload.size() == 2 && afterReload.get(0) == orderSecond && afterReload.get(1) == orderThird,
            "Formal unregister drops old order and later validate receives a new order");

        second.onChunkUnload();
        check(!SamLinkRegistry.findAll(world, "A", TileEntityAnnouncer.class).contains(second), "Chunk unload removes stale target");
        first.invalidate();
        check(SamLinkRegistry.findFirst(world, "A", TileEntityAnnouncer.class) == null, "Invalidate unregisters target");

        FixtureWorld clientWorld = new FixtureWorld(); clientWorld.isRemote = true;
        CountingAnnouncer client = new CountingAnnouncer(); client.setLinkKey("A"); clientWorld.add(client, 1, 0, 0);
        check(SamLinkRegistry.findAll(clientWorld, "A", TileEntityAnnouncer.class).isEmpty(), "Client world never creates link registry entries");
        SamLinkRegistry.clear(world); SamLinkRegistry.clear(another); SamLinkRegistry.clear(orderWorld);
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
            PacketSessionSpeakerRoutes routes = new PacketSessionSpeakerRoutes(17, 2, 0, 1);
            for (int i = 0; i < PacketLimits.SESSION_TARGETS; i++) routes.targets.add(route(i, 128, 1));
            buf.clear(); routes.toBytes(buf);
            check(buf.readableBytes() == 28 + PacketLimits.SESSION_TARGETS * 16,
                "Route snapshot chunk is bounded to 512 descriptors");
            PacketSessionSpeakerRoutes decoded = new PacketSessionSpeakerRoutes(); decoded.fromBytes(buf);
            check(decoded.sessionId == 17 && decoded.revision == 2
                    && decoded.targets.size() == PacketLimits.SESSION_TARGETS,
                "Route snapshot header and descriptors round trip");
            buf.clear(); buf.writeLong(17).writeLong(1).writeInt(1).writeInt(1).writeInt(0);
            expectInvalid(() -> new PacketSessionSpeakerRoutes().fromBytes(buf));
        } finally { buf.release(); }
    }
    private static void expectInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("Malformed input accepted"); }
        catch (DecoderException expected) { checks++; }
    }

    private static void expectEncodeInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid outgoing payload accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static void senderReceiverValidation() {
        ByteBuf buf = Unpooled.buffer();
        try {
            String asciiLimit = String.join("", Collections.nCopies(PacketLimits.LINK_KEY, "x"));
            PacketDebugConfig maxKey = new PacketDebugConfig(1, 2, 3, asciiLimit);
            maxKey.toBytes(buf);
            PacketDebugConfig maxKeyRead = new PacketDebugConfig(); maxKeyRead.fromBytes(buf);
            check(asciiLimit.equals(maxKeyRead.linkKey), "Bounded string accepts exactly the Java character limit");
            buf.clear();
            expectEncodeInvalid(() -> new PacketDebugConfig(1, 2, 3, asciiLimit + "x").toBytes(buf));

            String utf8Limit = String.join("", Collections.nCopies(PacketLimits.LINK_KEY, "\u754c"));
            buf.clear(); new PacketDebugConfig(1, 2, 3, utf8Limit).toBytes(buf);
            PacketDebugConfig utf8Read = new PacketDebugConfig(); utf8Read.fromBytes(buf);
            check(utf8Limit.equals(utf8Read.linkKey), "Sender and receiver accept the same multibyte UTF-8 boundary");
            buf.clear();
            expectEncodeInvalid(() -> new PacketDebugConfig(1, 2, 3, utf8Limit + "\u754c").toBytes(buf));

            List<TrainTypeCondition> maxConditions = new ArrayList<>();
            for (int i = 0; i < PacketLimits.CONDITIONS; i++)
                maxConditions.add(new TrainTypeCondition(String.join("", Collections.nCopies(PacketLimits.NAME, "k")), i % 4));
            PacketTrainTypeConfig train = new PacketTrainTypeConfig(1, 2, 3, maxConditions, "key", true);
            buf.clear(); train.toBytes(buf);
            PacketTrainTypeConfig trainRead = new PacketTrainTypeConfig(); trainRead.fromBytes(buf);
            check(trainRead.conditions.size() == PacketLimits.CONDITIONS, "Condition count and key length boundaries round trip");
            maxConditions.add(new TrainTypeCondition("extra", 0));
            buf.clear(); expectEncodeInvalid(() -> train.toBytes(buf));
            PacketTrainTypeConfig badType = new PacketTrainTypeConfig(1, 2, 3,
                Collections.singletonList(new TrainTypeCondition("key", 4)), "key", false);
            buf.clear(); expectEncodeInvalid(() -> badType.toBytes(buf));
            buf.clear(); buf.writeInt(1).writeInt(2).writeInt(3).writeInt(1);
            PacketLimits.writeString(buf, "key", PacketLimits.NAME); buf.writeInt(4);
            PacketLimits.writeString(buf, "key", PacketLimits.LINK_KEY); buf.writeBoolean(false);
            expectInvalid(() -> new PacketTrainTypeConfig().fromBytes(buf));

            for (int range : new int[] {0, PacketLimits.MAX_RANGE + 1}) {
                buf.clear(); expectEncodeInvalid(() -> new PacketSpeakerConfig(1, 2, 3, "key", range, 1).toBytes(buf));
                buf.clear(); buf.writeInt(1).writeInt(2).writeInt(3);
                PacketLimits.writeString(buf, "key", PacketLimits.LINK_KEY);
                buf.writeInt(range).writeFloat(1);
                expectInvalid(() -> new PacketSpeakerConfig().fromBytes(buf));
            }
            for (float volume : new float[] {-0.1F, 1.1F, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
                buf.clear(); expectEncodeInvalid(() -> new PacketSpeakerConfig(1, 2, 3, "key", 16, volume).toBytes(buf));
                buf.clear(); buf.writeInt(1).writeInt(2).writeInt(3);
                PacketLimits.writeString(buf, "key", PacketLimits.LINK_KEY);
                buf.writeInt(16).writeFloat(volume);
                expectInvalid(() -> new PacketSpeakerConfig().fromBytes(buf));
            }

            String maxSounds = String.join(",", Collections.nCopies(PacketLimits.SOUNDS, "s"));
            PacketAwarenessConfig awareness = new PacketAwarenessConfig(1, 2, 3, "key", maxSounds,
                PacketLimits.MAX_TICKS, true, false, true, PacketLimits.MAX_TICKS);
            buf.clear(); awareness.toBytes(buf);
            PacketAwarenessConfig awarenessRead = new PacketAwarenessConfig(); awarenessRead.fromBytes(buf);
            check(awarenessRead.soundList.equals(maxSounds), "Awareness sound and tick boundaries round trip");

            String wireSoundId = String.join("", Collections.nCopies(21, "\u754c"));
            List<String> wireSounds = new ArrayList<>(Collections.nCopies(PacketLimits.SOUNDS, wireSoundId));
            String maxWireSoundList = String.join(",", wireSounds);
            check(maxWireSoundList.length() <= PacketLimits.SOUND_LIST
                    && maxWireSoundList.getBytes(java.nio.charset.StandardCharsets.UTF_8).length == PacketLimits.MAX_UTF8_WIRE_BYTES,
                "Awareness sound list fixture reaches the exact Forge UTF-8 wire boundary");
            PacketAwarenessConfig wireBoundary = new PacketAwarenessConfig(1, 2, 3, "key", maxWireSoundList,
                20, false, false, false, 0);
            check(PacketLimits.sounds(maxWireSoundList) && wireBoundary.isValidPayload(),
                "Awareness accepts a 16383-byte sound list");
            buf.clear(); wireBoundary.toBytes(buf);
            PacketAwarenessConfig wireBoundaryRead = new PacketAwarenessConfig(); wireBoundaryRead.fromBytes(buf);
            check(maxWireSoundList.equals(wireBoundaryRead.soundList) && buf.readableBytes() == 0,
                "Awareness 16383-byte sound list round trips");

            wireSounds.set(0, wireSoundId + "x");
            String overWireSoundList = String.join(",", wireSounds);
            check(wireSounds.size() == PacketLimits.SOUNDS && wireSounds.get(0).length() <= PacketLimits.NAME
                    && overWireSoundList.length() <= PacketLimits.SOUND_LIST
                    && overWireSoundList.getBytes(java.nio.charset.StandardCharsets.UTF_8).length == PacketLimits.MAX_UTF8_WIRE_BYTES + 1,
                "Awareness over-limit fixture exceeds only the Forge UTF-8 wire boundary");
            PacketAwarenessConfig overWireBoundary = new PacketAwarenessConfig(1, 2, 3, "key", overWireSoundList,
                20, false, false, false, 0);
            check(!PacketLimits.sounds(overWireSoundList) && !overWireBoundary.isValidPayload(),
                "Awareness rejects a 16384-byte sound list in shared payload validation");
            buf.clear(); expectEncodeInvalid(() -> overWireBoundary.toBytes(buf));
            check(buf.writerIndex() == 0, "Awareness rejects an oversized wire string before encoding starts");

            buf.clear(); expectEncodeInvalid(() -> new PacketAwarenessConfig(1, 2, 3, "key",
                maxSounds + ",extra", 20, false, false, false, 0).toBytes(buf));
            buf.clear(); expectEncodeInvalid(() -> new PacketAwarenessConfig(1, 2, 3, "key", "s",
                19, false, false, false, 0).toBytes(buf));
            buf.clear(); buf.writeInt(1).writeInt(2).writeInt(3);
            PacketLimits.writeString(buf, "key", PacketLimits.LINK_KEY);
            PacketLimits.writeString(buf, maxSounds + ",extra", PacketLimits.SOUND_LIST);
            buf.writeInt(20).writeBoolean(false).writeBoolean(false).writeBoolean(false).writeInt(0);
            expectInvalid(() -> new PacketAwarenessConfig().fromBytes(buf));

            PacketDepartureMelodyConfig melody = new PacketDepartureMelodyConfig(1, 2, 3, "key",
                String.join("", Collections.nCopies(PacketLimits.NAME, "s")),
                String.join("", Collections.nCopies(PacketLimits.NAME, "j")));
            buf.clear(); melody.toBytes(buf); new PacketDepartureMelodyConfig().fromBytes(buf);
            melody.scriptName += "x";
            buf.clear(); expectEncodeInvalid(() -> melody.toBytes(buf));

            PacketDepartureSwitchConfig invalidOffset = new PacketDepartureSwitchConfig(1, 2, 3,
                "key", "model", 0, Float.NaN, 0, 0);
            buf.clear(); expectEncodeInvalid(() -> invalidOffset.toBytes(buf));
            buf.clear(); buf.writeInt(1).writeInt(2).writeInt(3);
            PacketLimits.writeString(buf, "key", PacketLimits.LINK_KEY);
            PacketLimits.writeString(buf, "model", PacketLimits.MODEL);
            buf.writeInt(0).writeFloat(Float.POSITIVE_INFINITY).writeFloat(0).writeFloat(0);
            expectInvalid(() -> new PacketDepartureSwitchConfig().fromBytes(buf));
            for (float offset : new float[] {16.000002F, -16.000002F}) {
                PacketDepartureSwitchConfig unsafe = new PacketDepartureSwitchConfig(1, 2, 3,
                    "key", "model", 0, offset, 0, 0);
                buf.clear(); expectEncodeInvalid(() -> unsafe.toBytes(buf));
                buf.clear(); buf.writeInt(1).writeInt(2).writeInt(3);
                PacketLimits.writeString(buf, "key", PacketLimits.LINK_KEY);
                PacketLimits.writeString(buf, "model", PacketLimits.MODEL);
                buf.writeInt(0).writeFloat(offset).writeFloat(0).writeFloat(0);
                expectInvalid(() -> new PacketDepartureSwitchConfig().fromBytes(buf));
            }
            PacketDepartureSwitchConfig boundaryOffset = new PacketDepartureSwitchConfig(1, 2, 3,
                "key", "model", 0, -16, 16, 0);
            buf.clear(); boundaryOffset.toBytes(buf); new PacketDepartureSwitchConfig().fromBytes(buf);
            String modelLimit = String.join("", Collections.nCopies(PacketLimits.MODEL, "m"));
            PacketDepartureSwitchItemConfig maxModel = new PacketDepartureSwitchItemConfig(8, modelLimit);
            buf.clear(); maxModel.toBytes(buf);
            PacketDepartureSwitchItemConfig maxModelRead = new PacketDepartureSwitchItemConfig(); maxModelRead.fromBytes(buf);
            check(modelLimit.equals(maxModelRead.modelName), "Model boundary remains unchanged");
            buf.clear(); expectEncodeInvalid(() -> new PacketDepartureSwitchItemConfig(8, modelLimit + "m").toBytes(buf));
            buf.clear(); expectEncodeInvalid(() -> new PacketDepartureSwitchItemConfig(9, "model").toBytes(buf));

            PacketAnnounce announce = start(901);
            announce.bodySounds = Collections.singletonList(String.join("", Collections.nCopies(PacketLimits.NAME + 1, "s")));
            announce.bodyIntervalTicks = Collections.singletonList(0);
            announce.bodyPartTicks = Collections.singletonList(20);
            buf.clear(); expectEncodeInvalid(() -> announce.toBytes(buf));
            PacketDepartureStart departure = departure(902);
            departure.departure.melodyTicks = PacketAnnounce.MAX_DURATION_TICKS + 1;
            buf.clear(); expectEncodeInvalid(() -> departure.toBytes(buf));

            PacketSpeakerFallback fallback = new PacketSpeakerFallback(903);
            fallback.targets.add(new PacketSpeakerFallback.Target(1, 0, 1));
            buf.clear(); expectEncodeInvalid(() -> fallback.toBytes(buf));
            PacketSessionSpeakerRoutes routes = new PacketSessionSpeakerRoutes(904, 1, 0, 1);
            routes.targets.add(new PacketSessionSpeakerRoutes.Target(1, 16, Float.NaN));
            buf.clear(); expectEncodeInvalid(() -> routes.toBytes(buf));
        } finally { buf.release(); }
    }

    private static PacketAnnounce start(long id) {
        PacketAnnounce packet = new PacketAnnounce(new AnnounceData("", Collections.singletonList("test:body"), ""), "A", false, 0, 0, 0);
        packet.resolveTiming(Collections.singletonMap("test:body", 20));
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
        TileEntityAnnouncer owner = new TileEntityAnnouncer(); owner.setLinkKey("A"); world.add(owner, -10, 0, 0);
        for (int i = 0; i < 10; i++) { Speaker speaker = new Speaker(); speaker.linkKey = "A"; world.add(speaker, i, 0, 0); }
        List<Player> nearby = new ArrayList<>();
        for (int i = 0; i < 3; i++) nearby.add(player(world, i, 0, 0));
        for (int i = 0; i < 10; i++) player(world, 10000+i, 0, 0);
        RecordingDelivery out = new RecordingDelivery(); ServerSessions.delivery = out;
        PacketAnnounce routed = start(0); routed.repeatCount = 2;
        routed.startMelo = "test:a"; routed.arrMelo = "test:a";
        routed.bodySounds = Arrays.asList("test:body", "test:body", "test:body");
        routed.bodyIntervalTicks = Arrays.asList(0, 0, 0);
        long id = ServerSessions.start(owner, routed);
        check(out.count(PacketAnnounce.class) == 13 && out.count(PacketSessionSpeakerRoutes.class) == 13,
            "Each World player receives one START and one bounded route snapshot chunk");
        Set<EntityPlayerMP> unique = new HashSet<>(out.players);
        check(unique.size() == 13 && unique.containsAll(nearby), "START recipients are World-scoped and deduplicated");
        for (IMessage message : out.messages) if (message instanceof PacketAnnounce) {
            PacketAnnounce packet = (PacketAnnounce)message;
            check(packet.targets.length == 0, "Dynamic START carries no fixed Speaker targets");
            check(packet.repeatCount == 2, "Per-recipient START copy preserves repeat count");
            check(packet.bodyPartTicks.equals(Arrays.asList(20, 20, 20)),
                "Per-recipient START copy preserves server-authoritative timing");
            check(packet.startMeloTicks == 20 && packet.arrMeloTicks == 20,
                "Per-recipient START copy preserves melody timing");
        }
        out.clear();
        PacketMissingSpeakers missing = new PacketMissingSpeakers(id, new long[] {SpeakerRegistry.position(0, 0, 0), SpeakerRegistry.position(999, 0, 0)});
        ServerSessions.missing(nearby.get(0), missing);
        check(out.messages.isEmpty(), "Dynamic session does not expose coordinate fallback lookup");
        Player outsider = (Player)world.playerEntities.get(12);
        ServerSessions.missing(outsider, missing);
        check(out.messages.isEmpty(), "Non-recipient cannot request Speaker settings");
        nearby.get(0).posX = 50000;
        out.clear(); ServerSessions.stopKey(world, "A");
        check(out.messages.size() == 13 && out.players.contains(nearby.get(0)), "STOP reaches moved recipient");
        check(((PacketAnnounceStop)out.messages.get(0)).sessionId == id, "STOP names old session");
        out.clear(); ServerSessions.stopKey(world, "A");
        check(out.messages.isEmpty(), "STOP releases session recipients");
        nearby.get(0).posX = 0;
        long first = ServerSessions.start(owner, departure(0));
        out.clear(); ServerSessions.control(first, false);
        check(out.messages.size() == 13 && !((PacketDepartureControl)out.messages.get(0)).cancel, "OFF reaches original recipients");
        long second = ServerSessions.start(owner, departure(0));
        check(first != second, "Every start gets unique ID");
        out.clear(); ServerSessions.control(first, true);
        check(out.messages.size() == 13 && ((PacketDepartureControl)out.messages.get(0)).sessionId == first, "Old CANCEL names only old session");
        out.clear(); ServerSessions.control(second, false);
        check(out.messages.size() == 13, "New session survives old CANCEL");
        ServerSessions.INSTANCE.logout(new PlayerEvent.PlayerLoggedOutEvent(nearby.get(0)));
        out.clear(); ServerSessions.control(second, false);
        check(out.messages.size() == 12, "Logout removes original recipient");
        nearby.get(1).worldObj = new FixtureWorld();
        ServerSessions.INSTANCE.changedWorld(new PlayerEvent.PlayerChangedDimensionEvent(nearby.get(1), 0, 1));
        out.clear(); ServerSessions.control(second, false);
        check(out.messages.size() == 11, "World change cleans recipient");
        ServerSessions.finished(nearby.get(2), second);
        out.clear(); ServerSessions.control(second, false);
        check(out.messages.size() == 10, "Completion releases only that recipient");
        ServerSessions.stopKey(world, "A");
        SpeakerRegistry.clear(world);
        PacketAnnounce local = start(0); local.playLocalSound = true;
        ServerSessions.start(owner, local);
        check(out.around == 0 && !out.messages.isEmpty(), "Local routing also uses one World-scoped logical START");
        out.clear();
        for (Object obj : world.playerEntities) if (obj instanceof EntityPlayerMP) ((EntityPlayerMP)obj).posX += 100;
        for (int i = 0; i < ServerSessions.CLEANUP_INTERVAL_TICKS * 2; i++) endTick();
        check(out.messages.isEmpty(), "Player movement and periodic cleanup emit no routing packets");
        ServerSessions.clear();
    }

    private static void canonicalOrdinaryTiming() {
        Map<String, Integer> saved = new HashMap<>(AnnouncePackLoader.soundTicks);
        try {
            AnnouncePackLoader.soundTicks.clear();
            AnnouncePackLoader.soundTicks.put("test:start", 2);
            AnnouncePackLoader.soundTicks.put("test:one", 3);
            AnnouncePackLoader.soundTicks.put("test:two", 4);
            AnnouncePackLoader.soundTicks.put("test:arr", 5);
            FixtureWorld serverWorld = new FixtureWorld();
            TileEntityAnnouncer owner = new TileEntityAnnouncer(); owner.setLinkKey("A");
            serverWorld.add(owner, 0, 0, 0);
            PacketAnnounce resolved = new PacketAnnounce(new AnnounceData("test:start",
                Arrays.asList("test:one", "", "test:two"), Arrays.asList(0, 6, 0), "test:arr", 3),
                "A", true, 0, 0, 0);
            check(ServerSessions.start(owner, resolved) != 0
                && resolved.startMeloTicks == 2 && resolved.arrMeloTicks == 5
                && resolved.bodyPartTicks.equals(Arrays.asList(3, 6, 4)),
                "Server resolves start, body, interval and arrival durations once before START");

            PacketAnnounce missing = new PacketAnnounce(new AnnounceData("", Collections.singletonList("test:missing"), ""),
                "A", false, 0, 0, 0);
            check(ServerSessions.start(owner, missing) == 0, "Missing ordinary duration rejects START on the server");
            AnnouncePackLoader.soundTicks.put("test:invalid", 0);
            PacketAnnounce invalid = new PacketAnnounce(new AnnounceData("", Collections.singletonList("test:invalid"), ""),
                "A", false, 0, 0, 0);
            check(ServerSessions.start(owner, invalid) == 0, "Zero ordinary duration rejects START on the server");
            AnnouncePackLoader.soundTicks.put("test:invalid", PacketAnnounce.MAX_DURATION_TICKS + 1);
            check(ServerSessions.start(owner, invalid) == 0, "Oversized ordinary duration rejects START on the server");

            PacketAnnounce timeline = new PacketAnnounce(new AnnounceData("test:start",
                Arrays.asList("test:one", "", "test:two"), Arrays.asList(0, 2, 0), null, 2),
                "A", true, 0, 0, 0);
            timeline.sessionId = 810;
            timeline.resolveTiming(AnnouncePackLoader.soundTicks);
            FixtureWorld clientWorld = new FixtureWorld(); clientWorld.isRemote = true;
            TestClient client = new TestClient(); client.world = clientWorld;
            AnnouncePackLoader.soundTicks.clear(); // Prove ordinary playback has no client-local fallback.
            receiveReady(client, timeline);
            for (int i = 0; i < 29; i++) client.tick();
            check(client.playedAtTicks.equals(Arrays.asList(1, 4, 10, 15, 18, 24))
                && soundNames(client).equals(Arrays.asList("test:start", "test:one", "test:two",
                    "test:start", "test:one", "test:two")),
                "Client uses packet timing for boundaries and repeats with an empty local duration table");

            PacketAnnounce loop = new PacketAnnounce(new AnnounceData("", Collections.<String>emptyList(), "test:arr"),
                "A", true, 0, 0, 0);
            loop.sessionId = 811;
            loop.resolveTiming(Collections.singletonMap("test:arr", 3));
            TestClient loopClient = new TestClient(); loopClient.world = clientWorld;
            AnnouncePackLoader.soundTicks.put("test:arr", 55);
            receiveReady(loopClient, loop);
            for (int i = 0; i < 5; i++) loopClient.tick();
            check(loopClient.playedAtTicks.equals(Arrays.asList(1, 5)),
                "Arrival loop cadence uses server-authoritative duration despite a different client value");
        } finally {
            AnnouncePackLoader.soundTicks.clear();
            AnnouncePackLoader.soundTicks.putAll(saved);
            ServerSessions.clear();
        }
    }

    private static void departureInterval() throws Exception {
        ServerSessions.clear();
        FixtureWorld world = new FixtureWorld();
        TileEntityAnnouncer owner = new TileEntityAnnouncer(); owner.setLinkKey("A"); world.add(owner, 0, 0, 0);
        TileEntityAwarenessAnnouncer awareness = new TileEntityAwarenessAnnouncer();
        awareness.applyConfig("A", "test:a", 20, false, false, true, 3);
        world.add(awareness, 1, 0, 0);
        Speaker speaker = new Speaker(); speaker.linkKey = "A"; world.add(speaker, 2, 0, 0);
        player(world, 2, 0, 0);
        RecordingDelivery out = new RecordingDelivery(); ServerSessions.delivery = out;

        owner.startDirectSound("test:a", PacketAnnounce.PRIORITY_AWARENESS, false);
        check(out.count(PacketAnnounce.class) == 1 && out.count(PacketSessionSpeakerRoutes.class) == 1,
            "Awareness session starts before departure completion");
        out.clear();
        owner.notifyDepartureMelodyFinished();
        check(out.messages.size() == 1 && out.messages.get(0) instanceof PacketAnnounceStop,
            "Queued Awareness is stopped when post-departure interval begins");
        out.clear();
        awareness.updateEntity(); awareness.updateEntity();
        check(out.messages.isEmpty(), "Awareness remains silent during post-departure interval");
        awareness.updateEntity();
        check(out.count(PacketAnnounce.class) == 1 && out.count(PacketSessionSpeakerRoutes.class) == 1,
            "Awareness starts when post-departure interval expires");

        NBTTagCompound pendingSave = new NBTTagCompound();
        awareness.scheduleAfterDeparture();
        awareness.writeToNBT(pendingSave);
        check(!pendingSave.hasKey("pendingDepartureTicks"),
            "Post-departure pending state is not persisted");
        check(!((net.minecraft.network.play.server.S35PacketUpdateTileEntity) awareness.getDescriptionPacket())
            .func_148857_g().hasKey("pendingDepartureTicks"),
            "Post-departure pending state is not included in the description packet");

        FixtureWorld reloadedWorld = new FixtureWorld();
        TileEntityAnnouncer reloadedOwner = new TileEntityAnnouncer(); reloadedOwner.setLinkKey("A");
        reloadedWorld.add(reloadedOwner, 0, 0, 0);
        TileEntityAwarenessAnnouncer reloaded = new TileEntityAwarenessAnnouncer();
        pendingSave.setInteger("pendingDepartureTicks", 0); // Legacy world data must be ignored.
        reloaded.readFromNBT(pendingSave);
        reloadedWorld.add(reloaded, 1, 0, 0);
        Speaker reloadedSpeaker = new Speaker(); reloadedSpeaker.linkKey = "A"; reloadedWorld.add(reloadedSpeaker, 2, 0, 0);
        player(reloadedWorld, 2, 0, 0);
        out.clear();
        reloaded.updateEntity(); reloaded.updateEntity(); reloaded.updateEntity();
        check(out.messages.isEmpty(), "Reload does not revive a legacy post-departure pending event");
        SamTriggerDispatcher.dispatch(reloadedWorld,
            new SamTrigger(SamTriggerType.DEPARTURE_FINISHED, "A", 0, 0, 0,
                SamTriggerSourceType.INTERNAL, SamTrigger.NO_FORMATION));
        reloaded.updateEntity(); reloaded.updateEntity();
        check(out.messages.isEmpty(), "A new departure trigger observes the configured delay after reload");
        reloaded.updateEntity();
        check(out.count(PacketAnnounce.class) == 1 && out.count(PacketSessionSpeakerRoutes.class) == 1,
            "A new departure trigger schedules Awareness normally after reload");

        NBTTagCompound legacyPositive = (NBTTagCompound) pendingSave.copy();
        legacyPositive.setInteger("pendingDepartureTicks", 10);
        TileEntityAwarenessAnnouncer legacyLoaded = new TileEntityAwarenessAnnouncer();
        legacyLoaded.readFromNBT(legacyPositive);
        NBTTagCompound rewritten = new NBTTagCompound(); legacyLoaded.writeToNBT(rewritten);
        check(!rewritten.hasKey("pendingDepartureTicks"), "Positive legacy pending state is ignored and removed on rewrite");

        TileEntityAwarenessAnnouncer defaults = new TileEntityAwarenessAnnouncer();
        defaults.readFromNBT(new NBTTagCompound());
        check(defaults.departureDelayTicks == 0, "Missing post-departure interval defaults to zero");
        ServerSessions.clear(); SpeakerRegistry.clear(world); SpeakerRegistry.clear(reloadedWorld);
        LoadedSamTiles.clear(world); LoadedSamTiles.clear(reloadedWorld);
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
        check(selector.getLinkKey().equals("A"), "Invalid train condition type rejected");
        TileEntityStartAnnouncer start = new TileEntityStartAnnouncer(); world.add(start, 3, 0, 0);
        new NetworkHandler.StartAnnouncerConfigHandler().onMessage(new PacketStartAnnouncerConfig(3, 0, 0, "A", true), context);
        check(start.getLinkKey().isEmpty(), "Start config is queued"); serverTick(); check(start.isControlCar, "Start config applied");
        TileEntityStopAnnouncer stop = new TileEntityStopAnnouncer(); world.add(stop, 4, 0, 0);
        new NetworkHandler.StopAnnouncerConfigHandler().onMessage(new PacketStopAnnouncerConfig(4, 0, 0, "A", true), context);
        check(stop.getLinkKey().isEmpty(), "Stop config is queued"); serverTick(); check(stop.isControlCar, "Stop config applied");
        TileEntityDebugReceiver debug = new TileEntityDebugReceiver(); world.add(debug, 5, 0, 0);
        new PacketDebugConfig.Handler().onMessage(new PacketDebugConfig(5, 0, 0, " A "), context);
        check(debug.getLinkKey().isEmpty(), "Debug config is queued"); serverTick(); check(debug.getLinkKey().equals("A"), "Debug config normalized");
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
        Thread network = new Thread(() -> receiveReady(client, packet));
        network.start(); network.join();
        check(client.worldReads == 0 && client.played.isEmpty(), "Network thread touches no world or sound");
        Speaker speaker = new Speaker(); world.add(speaker, 0, 0, 0);
        client.tick(); check(client.played.isEmpty(), "Unsynchronized client TE is not authoritative");
        syncSpeaker(speaker, "A", 16, 1.0F);
        for (int i = 0; i < 20; i++) client.tick();
        check(client.played.isEmpty(), "Speaker synchronization does not restart a part already in progress");
        client.receive(packet); client.tick();
        check(client.played.isEmpty(), "Duplicate START does not replay");
        PacketAnnounce newer = start(101); newer.targets = packet.targets;
        receiveReady(client, newer, route(0, 16, 1)); client.receive(new PacketAnnounceStop(100)); client.tick();
        check(client.played.size() == 1 && client.live.size() == 1, "Old STOP cannot stop newer same-key session");
        client.receive(new PacketAnnounceStop(101)); client.tick();
        check(client.live.isEmpty(), "Matching STOP stops sound");
        PacketDepartureStart dep = departure(200); dep.targets = packet.targets;
        int departureFirst = client.played.size();
        receiveReady(client, dep, route(0, 16, 1)); client.receive(new PacketDepartureControl(200, false)); client.tick();
        check(client.played.get(departureFirst).getPositionedSoundLocation().toString().equals("test:m"), "START initializes melody before same-tick OFF");
        client.receive(new PacketDepartureControl(199, true)); client.tick();
        check(!client.ended.contains(200L), "Stale departure CANCEL ignored");
        client.receive(new PacketDepartureControl(200, true)); client.tick();
        check(client.live.isEmpty(), "Departure CANCEL stops only its channels");
        PacketDepartureStart high = departure(300); high.targets = packet.targets;
        PacketAnnounce awareness = start(301); awareness.priority = PacketAnnounce.PRIORITY_AWARENESS; awareness.targets = packet.targets;
        int before = client.played.size();
        receiveReady(client, high, route(0, 16, 1)); receiveReady(client, awareness, route(0, 16, 1)); client.tick();
        check(client.played.size() == before+1, "Awareness waits behind departure priority");
        client.receive(new PacketDepartureControl(300, true)); client.tick();
        check(client.played.size() == before+2, "Waiting Awareness resumes after departure CANCEL");
        client.receive(new PacketAnnounceStop(0)); client.tick();
        PacketDepartureStart overlapHigh = departure(400); overlapHigh.targets = packet.targets;
        PacketAnnounce overlap = start(401); overlap.priority = 0; overlap.allowOverlap = true; overlap.targets = packet.targets;
        before = client.played.size(); receiveReady(client, overlapHigh, route(0, 16, 1));
        receiveReady(client, overlap, route(0, 16, 1)); client.tick();
        check(client.played.size() == before+2, "Awareness allowOverlap preserved");
        client.world = new FixtureWorld(); client.tick();
        check(client.live.isEmpty() && ClientSpeakerRegistry.findByKey(world, "A").isEmpty(),
            "World identity change clears sessions, sounds and the client Speaker index");
        int acknowledgements = client.ended.size();
        PacketAnnounce normal = start(500); normal.playLocalSound = true;
        normal.bodySounds = Arrays.asList("test:body", "test:body");
        normal.bodyIntervalTicks = Arrays.asList(0, 0);
        normal.resolveTiming(Collections.singletonMap("test:body", 20));
        receiveReady(client, normal); client.tick();
        for (int i = 0; i < 45; i++) client.tick();
        check(client.ended.size() == acknowledgements+1 && client.ended.contains(500L), "One completion acknowledgement per session, not per sound");
        PacketAnnounce missingStart = start(600); missingStart.targets = new long[] {SpeakerRegistry.position(100, 0, 0)};
        int requests = client.missing.size(); before = client.played.size();
        receiveReady(client, missingStart);
        for (int i = 0; i < 5; i++) client.tick();
        check(client.missing.size() == requests && client.played.size() == before, "Dynamic routing never requests fixed target coordinates");
        PacketSpeakerFallback fallback = new PacketSpeakerFallback(600);
        fallback.targets.add(new PacketSpeakerFallback.Target(missingStart.targets[0], 64, .75F));
        client.receive(fallback); client.tick();
        check(client.played.size() == before, "Unsolicited legacy fallback is ignored");
        for (int i = 0; i < 25; i++) client.tick();
        check(client.missing.size() == requests && client.live.isEmpty(), "Missing TE neither polls server nor leaves late sounds playing");

        TestClient bounded = new TestClient(); bounded.world = world;
        PacketAnnounce playing = start(650); playing.playLocalSound = true;
        receiveReady(bounded, playing); bounded.tick();
        check(!bounded.live.isEmpty(), "Queue overflow fixture starts an active sound");
        int readsBeforeOverflow = bounded.worldReads;
        Thread flood = new Thread(() -> {
            for (int i = 0; i < AnnounceManager.MAX_PENDING + 1; i++)
                bounded.receive(new PacketAnnounceStop(10000 + i));
        });
        flood.start();
        try { flood.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
        check(bounded.pendingCount() == AnnounceManager.MAX_PENDING,
            "Client pending queue accepts at most MAX_PENDING actions");
        check(bounded.overflowPending() && bounded.worldReads == readsBeforeOverflow && !bounded.live.isEmpty(),
            "Queue overflow performs no world or sound work on the network thread");
        bounded.receiveDuringStop = start(652);
        bounded.tick();
        check(bounded.overflowObservedDuringStop && bounded.pendingObservedDuringStop == 0,
            "Overflow remains latched and rejects packets received during recovery");
        check(bounded.pendingCount() == 0 && !bounded.overflowPending() && bounded.live.isEmpty()
                && clientSessionCount(bounded) == 0,
            "Recovery completion leaves no pending actions, sessions or playing sounds before unlatching");
        PacketAnnounce recovered = start(651); recovered.playLocalSound = true;
        receiveReady(bounded, recovered);
        check(bounded.pendingCount() == 2, "A new START and route queue normally after overflow recovery");
        bounded.tick();
        check(!bounded.live.isEmpty(), "Client accepts a new START after overflow recovery");
        bounded.receive(new PacketAnnounceStop(99999));
        bounded.world = new FixtureWorld(); bounded.tick();
        check(bounded.pendingCount() == 0 && !bounded.overflowPending() && bounded.live.isEmpty(),
            "World identity change clears pending and overflow state");
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
        PacketAnnounce intervalPacket = new PacketAnnounce(new AnnounceData(null,
            Arrays.asList("test:one", "", "test:two"), Arrays.asList(0, 3, 0), null, 1),
            "A", true, 0, 0, 0);
        intervalPacket.sessionId = 704;
        intervalPacket.resolveTiming(AnnouncePackLoader.soundTicks);
        receiveReady(interval, intervalPacket);
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
        packet.resolveTiming(AnnouncePackLoader.soundTicks);
        receiveReady(client, packet);
        for (int i = 0; i < ticks; i++) client.tick();
        return client;
    }

    private static List<String> soundNames(TestClient client) {
        List<String> result = new ArrayList<>();
        for (ISound sound : client.played) result.add(sound.getPositionedSoundLocation().toString());
        return result;
    }

    private static void receiveReady(TestClient client, PacketAnnounce packet,
        PacketSessionSpeakerRoutes.Target... targets) {
        client.receive(packet);
        client.receive(routes(packet.sessionId, 1, 0, 1, targets));
    }

    private static PacketAnnounce routed(long id, boolean local, String... sounds) {
        Map<String, Integer> lengths = new HashMap<>();
        for (String sound : sounds) lengths.put(sound, 2);
        PacketAnnounce packet = new PacketAnnounce(new AnnounceData("", Arrays.asList(sounds), ""), "A", local, 0, 0, 0);
        packet.sessionId = id;
        packet.resolveTiming(lengths);
        return packet;
    }

    private static void advanceToNextPart(TestClient client) {
        client.tick();
        client.tick();
        client.tick();
    }

    private static void dynamicRouting() {
        ClientSpeakerRegistry.clear();
        FixtureWorld world = new FixtureWorld(); world.isRemote = true;
        Speaker a = new Speaker(); world.add(a, 0, 0, 0); syncSpeaker(a, "A", 10, 1);
        Speaker b = new Speaker(); world.add(b, 100, 0, 0); syncSpeaker(b, "A", 10, 1);
        RoutingClient moving = new RoutingClient(); moving.world = world; moving.px = 50;
        receiveReady(moving, routed(1000, false, "test:r1", "test:r2", "test:r3", "test:r4", "test:r5"),
            route(0, 10, 1), route(100, 10, 1));
        moving.tick();
        moving.px = 0; advanceToNextPart(moving);
        moving.px = 100; advanceToNextPart(moving);
        moving.px = 50; advanceToNextPart(moving);
        moving.px = 100; advanceToNextPart(moving);
        check(soundNames(moving).equals(Arrays.asList("test:r2", "test:r3", "test:r5")),
            "Timeline advances while inaudible and resumes only at later part boundaries");
        check(moving.played.get(0).getXPosF() == .5F && moving.played.get(1).getXPosF() == 100.5F,
            "Moving from Speaker A to B re-resolves the physical source");

        FixtureWorld addedWorld = new FixtureWorld(); addedWorld.isRemote = true;
        RoutingClient added = new RoutingClient(); added.world = addedWorld; added.px = 20;
        receiveReady(added, routed(1001, false, "test:add1", "test:add2")); added.tick();
        Speaker late = new Speaker(); addedWorld.add(late, 20, 0, 0); syncSpeaker(late, "A", 8, 1);
        added.receive(routes(1001, 2, 0, 1, route(20, 8, 1)));
        advanceToNextPart(added);
        check(soundNames(added).equals(Collections.singletonList("test:add2")),
            "Speaker synchronized after START is available at the next part");

        FixtureWorld changedWorld = new FixtureWorld(); changedWorld.isRemote = true;
        Speaker changed = new Speaker(); changedWorld.add(changed, 0, 0, 0); syncSpeaker(changed, "A", 10, 1);
        RoutingClient changedClient = new RoutingClient(); changedClient.world = changedWorld; changedClient.px = 5;
        receiveReady(changedClient, routed(1002, false, "test:c1", "test:c2", "test:c3", "test:c4", "test:c5", "test:c6", "test:c7"),
            route(0, 10, 1));
        changedClient.tick();
        changed.onChunkUnload(); changedClient.receive(routes(1002, 2, 0, 1)); advanceToNextPart(changedClient);
        changed.validate(); changedClient.receive(routes(1002, 3, 0, 1, route(0, 10, 1))); advanceToNextPart(changedClient);
        changed.invalidate(); changedClient.receive(routes(1002, 4, 0, 1)); advanceToNextPart(changedClient);
        Speaker replacement = new Speaker(); changedWorld.add(replacement, 0, 0, 0); syncSpeaker(replacement, "B", 10, 1);
        advanceToNextPart(changedClient);
        syncSpeaker(replacement, "A", 1, 1); changedClient.receive(routes(1002, 5, 0, 1, route(0, 1, 1)));
        advanceToNextPart(changedClient);
        syncSpeaker(replacement, "A", 10, .5F); changedClient.receive(routes(1002, 6, 0, 1, route(0, 10, .5F)));
        advanceToNextPart(changedClient);
        check(soundNames(changedClient).equals(Arrays.asList("test:c1", "test:c3", "test:c7"))
                && changedClient.played.get(2).getVolume() == .3125F,
            "Unload, reload, destruction, replacement, unlink, range and volume changes affect the next part only");

        FixtureWorld localWorld = new FixtureWorld(); localWorld.isRemote = true;
        RoutingClient local = new RoutingClient(); local.world = localWorld; local.px = 30;
        receiveReady(local, routed(1003, true, "test:l1", "test:l2", "test:l3")); local.tick();
        local.px = 0; advanceToNextPart(local);
        local.px = 30; advanceToNextPart(local);
        check(soundNames(local).equals(Collections.singletonList("test:l2")),
            "Local sound checks the current player position at every part boundary");

        Map<String, Integer> loopLength = Collections.singletonMap("test:loop-dynamic", 2);
        PacketAnnounce loop = new PacketAnnounce(new AnnounceData("", Collections.<String>emptyList(), "test:loop-dynamic"), "A", false, 0, 0, 0);
        loop.sessionId = 1004; loop.resolveTiming(loopLength);
        RoutingClient looping = new RoutingClient(); looping.world = world; looping.px = 50;
        receiveReady(looping, loop, route(0, 10, 1), route(100, 10, 1));
        looping.tick(); looping.px = 0; advanceToNextPart(looping);
        looping.px = 50; advanceToNextPart(looping);
        check(soundNames(looping).equals(Collections.singletonList("test:loop-dynamic")),
            "Arrival melody re-resolves routing for each loop");

        RoutingClient departureClient = new RoutingClient(); departureClient.world = world; departureClient.px = 0;
        PacketDepartureStart departure = departure(1005);
        receiveReady(departureClient, departure, route(0, 10, 1), route(100, 10, 1)); departureClient.tick();
        departureClient.px = 100;
        departureClient.receive(new PacketDepartureControl(1005, false)); departureClient.tick();
        check(soundNames(departureClient).equals(Arrays.asList("test:m", "test:d"))
                && departureClient.played.get(0).getXPosF() == .5F
                && departureClient.played.get(1).getXPosF() == 100.5F,
            "Departure keeps one session identity while play events move from A to B");

        RoutingClient priority = new RoutingClient(); priority.world = new FixtureWorld(); priority.world.isRemote = true; priority.px = 0;
        PacketAnnounce awareness = routed(1006, true, "test:aware1", "test:aware2");
        awareness.priority = PacketAnnounce.PRIORITY_AWARENESS;
        PacketAnnounce remoteHigh = routed(1007, true, "test:high1"); remoteHigh.priority = PacketAnnounce.PRIORITY_DEPARTURE_MELODY; remoteHigh.x = 100;
        receiveReady(priority, awareness); receiveReady(priority, remoteHigh); priority.tick();
        check(soundNames(priority).equals(Collections.singletonList("test:aware1")),
            "Inaudible high-priority session does not suppress audible Awareness");
    }

    private static void initialRouteGate() {
        FixtureWorld world = new FixtureWorld(); world.isRemote = true;

        TestClient waiting = new TestClient(); waiting.world = world;
        PacketAnnounce waitingStart = routed(1200, true, "test:gate1", "test:gate2");
        waiting.receive(waitingStart);
        for (int i = 0; i < 6; i++) waiting.tick();
        check(waiting.played.isEmpty(), "START alone cannot advance ordinary playback before initial routes");
        waiting.receive(routes(1200, 1, 0, 1)); waiting.tick();
        check(soundNames(waiting).equals(Collections.singletonList("test:gate1")),
            "A complete empty snapshot is routing-ready and starts from the first part");
        int firstTick = waiting.playedAtTicks.get(0);
        advanceToNextPart(waiting);
        check(soundNames(waiting).equals(Arrays.asList("test:gate1", "test:gate2"))
                && waiting.playedAtTicks.get(1) - firstTick == 3,
            "Snapshot wait ticks do not consume the ordinary timeline");

        RoutingClient chunked = new RoutingClient(); chunked.world = world; chunked.px = 10;
        chunked.receive(routed(1201, false, "test:chunked"));
        chunked.receive(routes(1201, 1, 0, 2, route(0, 1, 1))); chunked.tick();
        check(chunked.played.isEmpty(), "A partial initial route revision cannot start playback");
        chunked.receive(routes(1201, 1, 1, 2, route(10, 2, 1))); chunked.tick();
        check(soundNames(chunked).equals(Collections.singletonList("test:chunked")),
            "The final initial route chunk atomically enables playback");

        TestClient priority = new TestClient(); priority.world = world;
        PacketAnnounce high = routed(1202, true, "test:gate-high");
        high.priority = PacketAnnounce.PRIORITY_DEPARTURE_MELODY;
        receiveReady(priority, high); priority.tick();
        int beforeCandidate = priority.played.size();
        PacketAnnounce low = routed(1203, true, "test:gate-rejected");
        receiveReady(priority, low); priority.tick();
        check(priority.played.size() == beforeCandidate && priority.ended.contains(1203L),
            "Initial priority rejection occurs before the candidate can play");

        TestClient deferredPriority = new TestClient(); deferredPriority.world = world;
        PacketAnnounce existing = routed(1207, true, "test:gate-existing");
        receiveReady(deferredPriority, existing); deferredPriority.tick();
        PacketAnnounce pendingHigh = routed(1208, true, "test:gate-pending-high");
        pendingHigh.priority = PacketAnnounce.PRIORITY_DEPARTURE_MELODY;
        deferredPriority.receive(pendingHigh); deferredPriority.tick();
        check(soundNames(deferredPriority).equals(Collections.singletonList("test:gate-existing"))
                && !deferredPriority.ended.contains(1207L),
            "An unready high-priority START cannot arbitrate or suppress an existing session");
        deferredPriority.receive(routes(1208, 1, 0, 1)); deferredPriority.tick();
        check(soundNames(deferredPriority).equals(Arrays.asList("test:gate-existing", "test:gate-pending-high"))
                && deferredPriority.ended.contains(1207L),
            "A high-priority session arbitrates only after its initial snapshot completes");

        RoutingClient departureWaiting = new RoutingClient(); departureWaiting.world = world; departureWaiting.px = 0;
        departureWaiting.receive(departure(1204));
        for (int i = 0; i < 3; i++) departureWaiting.tick();
        check(departureWaiting.played.isEmpty(),
            "Departure START cannot initialize or advance before initial routes");
        departureWaiting.receive(routes(1204, 1, 0, 1, route(0, 2, 1))); departureWaiting.tick();
        check(soundNames(departureWaiting).equals(Collections.singletonList("test:m")),
            "Departure initializes on the tick after its complete initial snapshot is accepted");

        RoutingClient earlyOff = new RoutingClient(); earlyOff.world = world; earlyOff.px = 0;
        earlyOff.receive(departure(1205));
        earlyOff.receive(new PacketDepartureControl(1205, false)); earlyOff.tick();
        check(earlyOff.played.isEmpty(), "Early departure OFF records state without pre-route playback");
        earlyOff.receive(routes(1205, 1, 0, 1, route(0, 2, 1))); earlyOff.tick();
        check(soundNames(earlyOff).equals(Arrays.asList("test:m", "test:d")),
            "Early departure OFF is applied from the released state after routing becomes ready");

        TestClient stopped = new TestClient(); stopped.world = world;
        stopped.receive(routed(1206, true, "test:stopped-before-routes")); stopped.tick();
        stopped.receive(new PacketAnnounceStop(1206)); stopped.tick();
        stopped.receive(routes(1206, 1, 0, 1));
        for (int i = 0; i < 3; i++) stopped.tick();
        check(stopped.played.isEmpty(), "STOP before initial routes prevents late route resurrection");
    }

    private static PacketSessionSpeakerRoutes routes(long sessionId, long revision, int chunkIndex,
        int chunkCount, PacketSessionSpeakerRoutes.Target... targets) {
        PacketSessionSpeakerRoutes packet = new PacketSessionSpeakerRoutes(sessionId, revision, chunkIndex, chunkCount);
        packet.targets.addAll(Arrays.asList(targets));
        return packet;
    }

    private static PacketSessionSpeakerRoutes.Target route(int x, int range, float volume) {
        return new PacketSessionSpeakerRoutes.Target(SpeakerRegistry.position(x, 0, 0), range, volume);
    }

    private static void serverDescriptorRouting() throws Exception {
        ClientSpeakerRegistry.clear();
        FixtureWorld world = new FixtureWorld(); world.isRemote = true;
        RoutingClient client = new RoutingClient(); client.world = world; client.px = 100;
        PacketAnnounce packet = routed(1100, false, "test:s1", "test:s2", "test:s3");
        client.receive(packet);
        client.receive(routes(1100, 1, 0, 1, route(0, 128, .25F)));
        client.tick();
        check(soundNames(client).equals(Collections.singletonList("test:s1"))
                && client.played.get(0).getVolume() == 2.0F,
            "A range-128 server descriptor plays when the client Speaker TE is not loaded");

        Speaker formal = new Speaker(); world.add(formal, 0, 0, 0); syncSpeaker(formal, "A", 128, .1F);
        advanceToNextPart(client);
        check(client.played.size() == 2 && client.played.get(1).getVolume() == .8F,
            "Synchronized client TE overrides its same-position server descriptor without duplication");
        formal.onChunkUnload();
        advanceToNextPart(client);
        check(client.played.size() == 3 && client.played.get(2).getVolume() == 2.0F,
            "Server descriptor remains usable after the client TE unloads");

        RoutingClient moving = new RoutingClient(); moving.world = world; moving.px = 0;
        moving.receive(routed(1101, false, "test:move1", "test:move2"));
        moving.receive(routes(1101, 1, 0, 1, route(0, 8, 1), route(100, 8, 1)));
        moving.tick(); moving.px = 100; advanceToNextPart(moving);
        check(moving.played.size() == 2 && moving.played.get(0).getXPosF() == .5F
                && moving.played.get(1).getXPosF() == 100.5F && moving.missing.isEmpty(),
            "Movement routes from loaded-area A to descriptor-only B without START or query packets");

        RoutingClient updated = new RoutingClient(); updated.world = world; updated.px = 5;
        updated.receive(routed(1109, false, "test:update1", "test:update2", "test:update3"));
        updated.receive(routes(1109, 1, 0, 1, route(0, 1, 1))); updated.tick();
        updated.receive(routes(1109, 2, 0, 1, route(0, 10, .25F))); updated.tick();
        check(updated.played.isEmpty(), "Route update does not start an in-progress sound");
        updated.tick(); updated.tick();
        updated.receive(routes(1109, 3, 0, 1, route(0, 10, .5F)));
        advanceToNextPart(updated);
        check(updated.played.size() == 2 && updated.played.get(0).getVolume() == .15625F
                && updated.played.get(1).getVolume() == .3125F,
            "Descriptor range and volume revisions apply at subsequent part boundaries");

        RoutingClient removal = new RoutingClient(); removal.world = world; removal.px = 0;
        removal.receive(routed(1110, false, "test:remove1", "test:remove2"));
        removal.receive(routes(1110, 1, 0, 1, route(0, 10, 1))); removal.tick();
        removal.receive(routes(1110, 2, 0, 1)); advanceToNextPart(removal);
        check(soundNames(removal).equals(Collections.singletonList("test:remove1")),
            "Empty removal revision prevents playback from the next part");

        RoutingClient stale = new RoutingClient(); stale.world = world; stale.px = 100;
        stale.receive(routed(1102, false, "test:rev1", "test:rev2"));
        stale.receive(routes(1102, 3, 0, 1, route(100, 10, 1)));
        stale.receive(routes(1102, 2, 0, 1, route(0, 10, 1)));
        stale.tick();
        check(stale.played.size() == 1 && stale.played.get(0).getXPosF() == 100.5F,
            "Older routing revision cannot replace a newer complete snapshot");
        stale.receive(routes(1102, 4, 0, 2, route(0, 10, 1)));
        advanceToNextPart(stale);
        check(stale.played.size() == 2 && stale.played.get(1).getXPosF() == 100.5F,
            "Incomplete newer revision does not replace the last complete snapshot");

        RoutingClient split = new RoutingClient(); split.world = world; split.px = 5120;
        split.receive(routed(1103, false, "test:split"));
        PacketSessionSpeakerRoutes first = routes(1103, 1, 0, 2);
        for (int i = 0; i < PacketLimits.SESSION_TARGETS; i++) first.targets.add(route(i * 10, 1, 1));
        PacketSessionSpeakerRoutes second = routes(1103, 1, 1, 2, route(5120, 1, 1));
        split.receive(first); split.receive(second); split.tick();
        check(split.played.size() == 1 && split.played.get(0).getXPosF() == 5120.5F
                && first.targets.size() == PacketLimits.SESSION_TARGETS && second.targets.size() == 1,
            "A split snapshot retains and routes all 513 descriptors within per-packet limits");

        RoutingClient priority = new RoutingClient(); priority.world = world; priority.px = 0;
        PacketAnnounce high = routed(1104, false, "test:descriptor-high");
        high.priority = PacketAnnounce.PRIORITY_DEPARTURE_MELODY;
        PacketAnnounce awareness = routed(1105, false, "test:descriptor-awareness");
        awareness.priority = PacketAnnounce.PRIORITY_AWARENESS;
        priority.receive(high); priority.receive(routes(1104, 1, 0, 1, route(0, 10, 1)));
        priority.receive(awareness); priority.receive(routes(1105, 1, 0, 1, route(0, 10, 1)));
        priority.tick();
        check(soundNames(priority).equals(Collections.singletonList("test:descriptor-high")),
            "Descriptor-only audible high priority session pauses Awareness");

        RoutingClient stopped = new RoutingClient(); stopped.world = world; stopped.px = 0;
        stopped.receive(routed(1106, false, "test:stop1", "test:stop2"));
        stopped.receive(routes(1106, 1, 0, 1, route(0, 10, 1))); stopped.tick();
        stopped.receive(new PacketAnnounceStop(1106)); stopped.tick();
        stopped.receive(routes(1106, 2, 0, 1, route(0, 10, 1))); advanceToNextPart(stopped);
        check(stopped.played.size() == 1 && stopped.live.isEmpty(),
            "STOP releases descriptors and ignores later route updates");

        RoutingClient completed = new RoutingClient(); completed.world = world; completed.px = 0;
        completed.receive(routed(1107, false, "test:complete"));
        completed.receive(routes(1107, 1, 0, 1, route(0, 10, 1))); completed.tick();
        advanceToNextPart(completed);
        completed.receive(routes(1107, 2, 0, 1, route(0, 10, 1))); completed.tick();
        check(completed.played.size() == 1 && completed.ended.contains(1107L),
            "Session completion releases descriptors and ignores later updates");

        RoutingClient switched = new RoutingClient(); switched.world = world; switched.px = 0;
        switched.receive(routed(1108, false, "test:world1", "test:world2"));
        switched.receive(routes(1108, 1, 0, 1, route(0, 10, 1))); switched.tick();
        switched.world = new FixtureWorld(); switched.world.isRemote = true; switched.tick();
        switched.receive(routes(1108, 2, 0, 1, route(0, 10, 1))); advanceToNextPart(switched);
        check(switched.played.size() == 1 && switched.live.isEmpty(),
            "World identity change releases session descriptors and rejects old-World updates");

        FixtureWorld serverWorld = new FixtureWorld();
        TileEntityAnnouncer ownerA = new TileEntityAnnouncer(); ownerA.setLinkKey("A"); serverWorld.add(ownerA, -2, 0, 0);
        TileEntityAnnouncer ownerB = new TileEntityAnnouncer(); ownerB.setLinkKey("B"); serverWorld.add(ownerB, -3, 0, 0);
        Speaker changed = new Speaker(); changed.linkKey = "A"; serverWorld.add(changed, 0, 0, 0);
        player(serverWorld, 0, 0, 0);
        RecordingDelivery out = new RecordingDelivery(); ServerSessions.delivery = out;
        long sessionA = ServerSessions.start(ownerA, start(0));
        PacketAnnounce bStart = start(0); bStart.linkKey = "B";
        long sessionB = ServerSessions.start(ownerB, bStart);
        out.clear(); changed.applyConfig("B", 24, .5F);
        check(out.messages.isEmpty(), "Speaker reindex queues routing updates until tick END");
        endTick();
        PacketSessionSpeakerRoutes updateA = out.routeFor(sessionA);
        PacketSessionSpeakerRoutes updateB = out.routeFor(sessionB);
        check(updateA != null && updateA.revision == 2 && updateA.targets.isEmpty()
                && updateB != null && updateB.revision == 2 && updateB.targets.size() == 1,
            "A to B reindex removes the route from A and adds it to B");
        out.clear(); changed.applyConfig("B", 40, .5F); endTick();
        PacketSessionSpeakerRoutes rangeUpdate = out.routeFor(sessionB);
        check(rangeUpdate != null && rangeUpdate.revision == 3 && rangeUpdate.targets.get(0).range == 40,
            "Range changes produce an event-driven authoritative descriptor update");
        out.clear(); changed.applyConfig("B", 40, .25F); endTick();
        PacketSessionSpeakerRoutes volumeUpdate = out.routeFor(sessionB);
        check(volumeUpdate != null && volumeUpdate.revision == 4 && volumeUpdate.targets.get(0).volume == .25F,
            "Volume changes produce an event-driven authoritative descriptor update");
        out.clear(); changed.invalidate(); endTick();
        PacketSessionSpeakerRoutes removed = out.routeFor(sessionB);
        check(removed != null && removed.revision == 5 && removed.targets.isEmpty(),
            "Server Speaker removal publishes an empty replacement snapshot");
        out.clear();
        ((EntityPlayerMP)serverWorld.playerEntities.get(0)).posX = 100;
        for (int i = 0; i < 10; i++) endTick();
        check(out.messages.isEmpty(), "Player movement and part timing produce no routing update packets");
        ServerSessions.clear();
    }

    private static void coalescedRouteUpdates() throws Exception {
        ServerSessions.clear(); SpeakerRegistry.clear();
        FixtureWorld world = new FixtureWorld();
        TileEntityAnnouncer owner = new TileEntityAnnouncer(); owner.setLinkKey("A"); world.add(owner, -1, 0, 0);
        player(world, 0, 0, 0);
        RecordingDelivery out = new RecordingDelivery(); ServerSessions.delivery = out;
        long session = ServerSessions.start(owner, start(0));
        check(out.count(PacketAnnounce.class) == 1 && out.count(PacketSessionSpeakerRoutes.class) == 1
                && out.routeFor(session).revision == 1,
            "START sends its initial empty revision immediately without waiting for tick END");

        out.clear(); List<Speaker> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Speaker speaker = new Speaker(); speaker.linkKey = "A"; world.add(speaker, i, 0, 0); ten.add(speaker);
        }
        check(out.messages.isEmpty(), "Ten same-key registrations emit no routing packets during mutation");
        endTick();
        PacketSessionSpeakerRoutes registered = out.routeFor(session);
        check(out.count(PacketSessionSpeakerRoutes.class) == 1 && registered.revision == 2
                && registered.targets.size() == 10,
            "Ten same-key registrations coalesce into one final revision containing all Speakers");

        out.clear(); for (Speaker speaker : ten) speaker.onChunkUnload();
        check(out.messages.isEmpty(), "Chunk-unload-style unregister burst emits no intermediate snapshots");
        endTick();
        PacketSessionSpeakerRoutes unregistered = out.routeFor(session);
        check(out.count(PacketSessionSpeakerRoutes.class) == 1 && unregistered.revision == 3
                && unregistered.targets.isEmpty(),
            "Ten unregisters coalesce into one final empty revision");

        Speaker original = new Speaker(); original.linkKey = "A"; world.add(original, 20, 0, 0); endTick();
        out.clear(); original.onChunkUnload();
        Speaker replacement = new Speaker(); replacement.linkKey = "A"; replacement.range = 48; replacement.volume = .75F;
        world.add(replacement, 20, 0, 0);
        check(out.messages.isEmpty(), "Unregister and replacement registration retain only dirty state before flush");
        endTick();
        PacketSessionSpeakerRoutes replaced = out.routeFor(session);
        check(out.count(PacketSessionSpeakerRoutes.class) == 1 && replaced.targets.size() == 1
                && replaced.targets.get(0).range == 48 && replaced.targets.get(0).volume == .75F,
            "Same-tick replacement publishes no intermediate empty snapshot");

        Speaker lateOld = new Speaker(); lateOld.linkKey = "A"; world.add(lateOld, 21, 0, 0); endTick();
        Speaker lateReplacement = new Speaker(); lateReplacement.linkKey = "A"; world.add(lateReplacement, 21, 0, 0); endTick();
        out.clear(); lateOld.invalidate(); endTick();
        check(out.messages.isEmpty(), "Late invalidation of a replaced TE creates no unnecessary dirty update");
        lateReplacement.onChunkUnload(); endTick(); out.clear();

        replacement.applyConfig("A", 32, .75F);
        replacement.applyConfig("A", 64, .75F);
        replacement.applyConfig("A", 64, .6F);
        replacement.applyConfig("A", 64, .5F);
        check(out.messages.isEmpty(), "Repeated range and volume changes are dirty-only until tick END");
        endTick();
        PacketSessionSpeakerRoutes settings = out.routeFor(session);
        check(out.count(PacketSessionSpeakerRoutes.class) == 1 && settings.targets.size() == 1
                && settings.targets.get(0).range == 64 && settings.targets.get(0).volume == .5F,
            "One coalesced snapshot contains only final range and volume values");

        TileEntityAnnouncer ownerB = new TileEntityAnnouncer(); ownerB.setLinkKey("B"); world.add(ownerB, -2, 0, 0);
        TileEntityAnnouncer ownerC = new TileEntityAnnouncer(); ownerC.setLinkKey("C"); world.add(ownerC, -3, 0, 0);
        PacketAnnounce startB = start(0); startB.linkKey = "B";
        PacketAnnounce startC = start(0); startC.linkKey = "C";
        long sessionB = ServerSessions.start(ownerB, startB);
        long sessionC = ServerSessions.start(ownerC, startC);
        out.clear();
        replacement.applyConfig("B", 64, .5F);
        replacement.applyConfig("C", 64, .5F);
        replacement.applyConfig("A", 64, .5F);
        check(out.messages.isEmpty(), "A to B to C to A reindexes produce no intermediate snapshots");
        endTick();
        check(out.count(PacketSessionSpeakerRoutes.class) == 3
                && out.routeFor(session).targets.size() == 1
                && out.routeFor(sessionB).targets.isEmpty() && out.routeFor(sessionC).targets.isEmpty(),
            "A, B and C dirty keys flush independently once with their final state");

        FixtureWorld otherWorld = new FixtureWorld();
        TileEntityAnnouncer otherOwner = new TileEntityAnnouncer(); otherOwner.setLinkKey("A"); otherWorld.add(otherOwner, -1, 0, 0);
        player(otherWorld, 0, 0, 0);
        long otherSession = ServerSessions.start(otherOwner, start(0));
        out.clear();
        Speaker worldOne = new Speaker(); worldOne.linkKey = "A"; world.add(worldOne, 30, 0, 0);
        Speaker worldTwo = new Speaker(); worldTwo.linkKey = "A"; otherWorld.add(worldTwo, 0, 0, 0);
        check(dirtyWorlds() == 2, "Dirty routing state isolates distinct World identities");
        endTick();
        check(out.routeFor(session) != null && out.routeFor(otherSession) != null,
            "Distinct Worlds flush only their own active sessions");

        PacketAnnounce startD = start(0); startD.linkKey = "D";
        TileEntityAnnouncer ownerD = new TileEntityAnnouncer(); ownerD.setLinkKey("D"); world.add(ownerD, -4, 0, 0);
        long sessionD = ServerSessions.start(ownerD, startD); out.clear();
        Speaker dirtyD = new Speaker(); dirtyD.linkKey = "D"; world.add(dirtyD, 40, 0, 0);
        ServerSessions.stopKey(world, "D");
        check(out.count(PacketAnnounceStop.class) == 1, "Dirty session can stop before route flush");
        out.clear(); endTick();
        check(out.routeFor(sessionD) == null, "Flush sends no route packet to a stopped session");

        FixtureWorld unloading = new FixtureWorld();
        TileEntityAnnouncer unloadingOwner = new TileEntityAnnouncer(); unloadingOwner.setLinkKey("A"); unloading.add(unloadingOwner, -1, 0, 0);
        player(unloading, 0, 0, 0);
        long unloadingSession = ServerSessions.start(unloadingOwner, start(0)); out.clear();
        Speaker unloadingSpeaker = new Speaker(); unloadingSpeaker.linkKey = "A"; unloading.add(unloadingSpeaker, 0, 0, 0);
        check(dirtyContains(unloading), "World has pending dirty routing state before unload");
        ServerSessions.INSTANCE.unload(new net.minecraftforge.event.world.WorldEvent.Unload(unloading));
        check(!dirtyContains(unloading), "World unload removes its dirty World reference");
        out.clear(); endTick();
        check(out.routeFor(unloadingSession) == null, "World unload prevents pending route transmission");

        Speaker pending = new Speaker(); pending.linkKey = "A"; world.add(pending, 50, 0, 0);
        check(dirtyWorlds() > 0, "Speaker mutation creates pending dirty state");
        ServerSessions.clear(); out.clear(); endTick();
        check(dirtyWorlds() == 0 && out.messages.isEmpty(), "ServerSessions.clear removes all pending route state");
        out.clear(); endTick();
        check(out.messages.isEmpty(), "A server tick with no dirty keys emits no routing packets");

        ServerSessions.clear(); SpeakerRegistry.clear();
        FixtureWorld reentrantWorld = new FixtureWorld();
        TileEntityAnnouncer reentrantOwner = new TileEntityAnnouncer(); reentrantOwner.setLinkKey("A");
        reentrantWorld.add(reentrantOwner, -1, 0, 0); player(reentrantWorld, 0, 0, 0);
        ReentrantDelivery reentrant = new ReentrantDelivery(reentrantWorld); ServerSessions.delivery = reentrant;
        long reentrantSession = ServerSessions.start(reentrantOwner, start(0)); reentrant.clear();
        Speaker firstMutation = new Speaker(); firstMutation.linkKey = "A"; reentrantWorld.add(firstMutation, 0, 0, 0);
        reentrant.armed = true; endTick();
        check(reentrant.routeFor(reentrantSession).targets.size() == 1 && dirtyContains(reentrantWorld),
            "Mutation during flush is retained for the next tick, not the active dirty batch");
        reentrant.clear(); endTick();
        check(reentrant.routeFor(reentrantSession).targets.size() == 2
                && reentrant.routeFor(reentrantSession).revision == 3,
            "Mutation raised during flush publishes one complete revision on the next tick");

        ServerSessions.clear(); SpeakerRegistry.clear(); ServerSessions.delivery = out;
        FixtureWorld denseWorld = new FixtureWorld();
        TileEntityAnnouncer denseOwner = new TileEntityAnnouncer(); denseOwner.setLinkKey("A"); denseWorld.add(denseOwner, -1, 0, 0);
        player(denseWorld, 0, 0, 0);
        long denseSession = ServerSessions.start(denseOwner, start(0)); out.clear();
        List<Speaker> dense = new ArrayList<>();
        for (int i = 0; i < PacketLimits.SESSION_TARGETS + 1; i++) {
            Speaker speaker = new Speaker(); speaker.linkKey = "A"; denseWorld.add(speaker, i, 0, 0); dense.add(speaker);
        }
        check(out.messages.isEmpty(), "A 513-Speaker load burst sends nothing before END flush");
        endTick();
        int total = 0; long denseRevision = -1;
        for (IMessage message : out.messages) if (message instanceof PacketSessionSpeakerRoutes) {
            PacketSessionSpeakerRoutes route = (PacketSessionSpeakerRoutes)message;
            if (route.sessionId == denseSession) {
                total += route.targets.size();
                if (denseRevision < 0) denseRevision = route.revision;
                else check(denseRevision == route.revision, "All split chunks share one coalesced revision");
            }
        }
        check(out.count(PacketSessionSpeakerRoutes.class) == 2 && total == PacketLimits.SESSION_TARGETS + 1
                && denseRevision == 2,
            "Coalesced 513-Speaker snapshot remains split without truncation");

        out.clear(); for (Speaker speaker : dense) speaker.onChunkUnload();
        check(out.messages.isEmpty(), "A 513-Speaker movement-style unload has no per-Speaker packet burst");
        endTick();
        check(out.count(PacketSessionSpeakerRoutes.class) == 1 && out.routeFor(denseSession).targets.isEmpty()
                && out.routeFor(denseSession).revision == 3,
            "A 513-Speaker unload coalesces to one final snapshot revision");
        ServerSessions.clear(); SpeakerRegistry.clear();
    }

    private static int dirtyWorlds() throws Exception {
        Field field = ServerSessions.class.getDeclaredField("dirtyRouteKeys"); field.setAccessible(true);
        return ((Map<?, ?>)field.get(null)).size();
    }

    private static boolean dirtyContains(World world) throws Exception {
        Field field = ServerSessions.class.getDeclaredField("dirtyRouteKeys"); field.setAccessible(true);
        return ((Map<?, ?>)field.get(null)).containsKey(world);
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

    private static void serverTaskQueueFairness() throws Exception {
        ServerTaskQueue queue = ServerTaskQueue.INSTANCE;
        queue.clear();
        FixtureWorld world = new FixtureWorld();
        Player playerA = player(world, 0, 0, 0);
        Player playerB = player(world, 1, 0, 0);
        List<Integer> playerAOrder = new ArrayList<>();
        List<String> executionOrder = new ArrayList<>();
        int acceptedA = 0;
        for (int i = 0; i < ServerTaskQueue.MAX_PENDING_PER_SENDER + 10; i++) {
            final int sequence = i;
            if (queue.enqueue(playerA, () -> {
                playerAOrder.add(sequence);
                executionOrder.add("A");
            })) acceptedA++;
        }
        check(acceptedA == ServerTaskQueue.MAX_PENDING_PER_SENDER
                && queue.pendingCount(playerA) == ServerTaskQueue.MAX_PENDING_PER_SENDER,
            "One sender cannot exceed its bounded pending queue");
        check(queue.enqueue(playerB, () -> executionOrder.add("B")) && queue.pendingCount(playerB) == 1,
            "A full sender queue does not reject another sender");
        serverTick();
        check(executionOrder.size() == ServerTaskQueue.MAX_PER_SENDER_PER_TICK + 1
                && "B".equals(executionOrder.get(1)) && queue.pendingCount(playerA) > 0,
            "Round robin runs another sender before draining the flood sender");
        check(playerAOrder.size() == ServerTaskQueue.MAX_PER_SENDER_PER_TICK,
            "One sender cannot consume the global per-tick budget");
        boolean fifo = true;
        for (int i = 0; i < playerAOrder.size(); i++) fifo &= playerAOrder.get(i) == i;
        check(fifo, "A sender retains FIFO order");

        queue.clear();
        Player[] players = new Player[8];
        int[] perPlayerRuns = new int[players.length];
        List<Integer> fairOrder = new ArrayList<>();
        boolean withinLimitsAccepted = true;
        for (int sender = 0; sender < players.length; sender++) {
            players[sender] = player(world, sender + 2, 0, 0);
            final int senderIndex = sender;
            for (int task = 0; task < ServerTaskQueue.MAX_PENDING_PER_SENDER; task++)
                withinLimitsAccepted &= queue.enqueue(players[sender], () -> {
                    perPlayerRuns[senderIndex]++;
                    fairOrder.add(senderIndex);
                });
        }
        check(withinLimitsAccepted, "Tasks within each sender limit are accepted");
        serverTick();
        check(fairOrder.size() == ServerTaskQueue.MAX_PER_TICK,
            "Global MAX_PER_TICK remains an execution ceiling");
        for (int sender = 0; sender < players.length; sender++) {
            check(fairOrder.get(sender) == sender,
                "The first scheduling round visits every active sender");
            check(perPlayerRuns[sender] == ServerTaskQueue.MAX_PER_SENDER_PER_TICK,
                "Busy senders receive equal per-tick service");
        }

        queue.clear();
        int globallyAccepted = 0;
        for (int sender = 0; sender < ServerTaskQueue.MAX_PENDING / ServerTaskQueue.MAX_PENDING_PER_SENDER + 1; sender++) {
            Player current = player(world, sender + 20, 0, 0);
            for (int task = 0; task < ServerTaskQueue.MAX_PENDING_PER_SENDER; task++)
                if (queue.enqueue(current, () -> {})) globallyAccepted++;
        }
        check(globallyAccepted == ServerTaskQueue.MAX_PENDING && queue.pendingCount() == ServerTaskQueue.MAX_PENDING,
            "Global pending memory remains bounded across many senders");

        queue.clear();
        final boolean[] survivedFailure = {false, false};
        queue.enqueue(playerA, () -> { throw new RuntimeException("expected queue test failure"); });
        queue.enqueue(playerB, () -> survivedFailure[0] = true);
        queue.enqueue(playerA, () -> survivedFailure[1] = true);
        serverTick();
        check(survivedFailure[0] && survivedFailure[1],
            "A failing task does not prevent either sender's following work");

        queue.clear();
        survivedFailure[0] = false;
        queue.enqueue(playerA, () -> { throw new AssertionError("logged-out task ran"); });
        queue.enqueue(playerB, () -> survivedFailure[0] = true);
        queue.logout(new PlayerEvent.PlayerLoggedOutEvent(playerA));
        check(queue.pendingCount(playerA) == 0 && queue.pendingCount(playerB) == 1 && queue.pendingCount() == 1,
            "Logout discards only that player's pending tasks");
        serverTick();
        check(survivedFailure[0], "Logout cleanup does not discard another player's task");

        queue.enqueue(playerA, () -> {});
        queue.changedDimension(new PlayerEvent.PlayerChangedDimensionEvent(playerA, 0, 1));
        check(queue.pendingCount(playerA) == 0, "Dimension change discards stale client tasks");
        queue.enqueue(playerA, () -> {});
        queue.respawn(new PlayerEvent.PlayerRespawnEvent(playerA));
        check(queue.pendingCount(playerA) == 0, "Respawn discards stale client tasks");

        queue.enqueue(playerA, () -> {});
        queue.enqueue(playerB, () -> {});
        queue.clear();
        check(queue.pendingCount() == 0 && queue.activeSenderCount() == 0,
            "clear resets all queues, counts and scheduler state");
        serverTick();
    }

    private static void limitsAndExpiry() throws Exception {
        cpw.mods.fml.common.Mod mod = StationAnnounceModCore.class.getAnnotation(cpw.mods.fml.common.Mod.class);
        check("0.2.2-beta".equals(StationAnnounceModCore.VERSION), "Expected network-incompatible SAM version");
        check(("[" + StationAnnounceModCore.VERSION + "]").equals(mod.acceptableRemoteVersions()),
            "Forge exact remote version gate follows SAM version");
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeLong(1).writeInt(PacketLimits.MISSING_TARGETS+1);
            expectInvalid(() -> new PacketMissingSpeakers().fromBytes(buf));
            buf.clear(); buf.writeLong(1).writeInt(PacketLimits.SESSION_TARGETS+1);
            expectInvalid(() -> new PacketSpeakerFallback().fromBytes(buf));
            PacketAnnounce bounded = start(1); bounded.targets = new long[PacketLimits.SESSION_TARGETS];
            bounded.bodySounds = Collections.nCopies(PacketLimits.BODY_SOUNDS, "test:body");
            bounded.bodyIntervalTicks = Collections.nCopies(PacketLimits.BODY_SOUNDS, 0);
            bounded.bodyPartTicks = Collections.nCopies(PacketLimits.BODY_SOUNDS, 20);
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
                buf.writeInt(0).writeInt(PacketAnnounce.TIMING_MAGIC).writeInt(repeat)
                    .writeInt(0).writeInt(0).writeInt(0);
                expectInvalid(() -> new PacketAnnounce().fromBytes(buf));
            }
            buf.clear(); bounded.writeHeader(buf);
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
            buf.writeInt(0);
            expectInvalid(() -> new PacketAnnounce().fromBytes(buf));

            buf.clear(); bounded.writeHeader(buf);
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
            buf.writeInt(1);
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "test:body");
            buf.writeInt(PacketAnnounce.TIMING_MAGIC).writeInt(1).writeInt(0).writeInt(0).writeInt(0);
            expectInvalid(() -> new PacketAnnounce().fromBytes(buf));

            buf.clear(); bounded.writeHeader(buf);
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "");
            buf.writeInt(1);
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, "test:body");
            buf.writeInt(PacketAnnounce.TIMING_MAGIC).writeInt(1).writeInt(0).writeInt(0).writeInt(1)
                .writeInt(PacketAnnounce.MAX_DURATION_TICKS + 1);
            expectInvalid(() -> new PacketAnnounce().fromBytes(buf));
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
        owner.setLinkKey("A"); world.add(owner, -10, 0, 0);
        for (int i = 0; i < 10; i++) { Speaker s = new Speaker(); s.linkKey = "A"; world.add(s, i, 0, 0); }
        Player player = player(world, 0, 0, 0); RecordingDelivery out = new RecordingDelivery(); ServerSessions.delivery = out;
        long id = ServerSessions.start(owner, start(0)); out.clear();
        ServerSessions.missing(player, new PacketMissingSpeakers(id, new long[11]));
        check(out.messages.isEmpty(), "Legacy multi-coordinate request returns no route data");
        ServerSessions.missing(player, new PacketMissingSpeakers(id, new long[] {SpeakerRegistry.position(0, 0, 0)}));
        check(out.messages.isEmpty(), "Legacy single-coordinate request returns no route data");
        new NetworkHandler.MissingSpeakersHandler().onMessage(
            new PacketMissingSpeakers(id, new long[] {SpeakerRegistry.position(0, 0, 0)}), context(player));
        serverTick();
        check(out.messages.isEmpty(), "Legacy missing-Speaker handler returns no client-selected coordinate data");
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
        for (int i = 0; i < ServerTaskQueue.MAX_PENDING_PER_SENDER+50; i++)
            ServerTaskQueue.INSTANCE.enqueue(player, () -> ran[0]++);
        new NetworkHandler.FinishedHandler().onMessage(new PacketSessionFinished(id), context(player));
        serverTick();
        check(ran[0] == ServerTaskQueue.MAX_PER_SENDER_PER_TICK && sessions() == 1,
            "Per-sender overflow drops that sender's ACK and bounds work per tick");
        for (int i = 0; i < 5; i++) serverTick();
        check(ran[0] == ServerTaskQueue.MAX_PENDING_PER_SENDER && sessions() == 1,
            "Per-sender overflow tasks are not retained indefinitely");
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
        ServerSessions.start(owner, local); ServerTaskQueue.INSTANCE.enqueue(player, () -> ran[0]++);
        new StationAnnounceModCore().serverStopped(null); int before = ran[0]; serverTick();
        check(sessions() == 0 && ran[0] == before, "Server stop clears sessions and pending tasks");

        // The sending side must never generate a packet rejected by its own decoder.
        for (int i = 0; i < PacketLimits.SESSION_TARGETS+10; i++) {
            Speaker s = new Speaker(); s.linkKey = "A"; s.range = 128;
            world.add(s, i%20, i/20, 0);
        }
        out.clear(); ServerSessions.start(owner, start(0));
        int described = 0;
        for (IMessage message : out.messages) if (message instanceof PacketSessionSpeakerRoutes)
            described += ((PacketSessionSpeakerRoutes)message).targets.size();
        check(out.count(PacketAnnounce.class) == 1 && out.count(PacketSessionSpeakerRoutes.class) == 2
                && described == PacketLimits.SESSION_TARGETS + 10,
            "START splits more than 512 Speaker descriptors without truncation");
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
            packet.bodyIntervalTicks = Arrays.asList(0, 0, 0);
            packet.resolveTiming(Collections.singletonMap("test:body", 20));
            Speaker formal = new Speaker(); world.add(formal, 0, 0, 0);
            check(!formal.isClientConfigSynced() && formal.linkKey.isEmpty()
                && formal.range == 16 && formal.volume == 1.0F, "New client Speaker TE is not configuration-synchronized");
            receiveReady(client, packet); client.tick();
            check(client.played.isEmpty() && client.missing.isEmpty(),
                "Unsynchronized existing Speaker TE is skipped without coordinate requests");
            PacketSpeakerFallback fallback = new PacketSpeakerFallback(900);
            fallback.targets.add(new PacketSpeakerFallback.Target(packet.targets[0], 64, .75F));
            client.receive(fallback); client.tick();
            check(client.played.isEmpty(), "Unsolicited fallback cannot authorize a Speaker");
            syncSpeaker(formal, currentKey, 16, .25F);
            check(formal.isClientConfigSynced(), "S35 application marks client Speaker configuration synchronized");
            client.receive(routes(900, 2, 0, 1, route(0, 16, .25F)));
            for (int i = 0; i < 20; i++) client.tick();
            if (currentKey.equals("A")) {
                check(client.played.size() == 1 && client.played.get(0).getVolume() == .25F,
                    "Next part uses synchronized TE range/volume from the client index");
            } else check(client.played.isEmpty(),
                "Synchronized mismatched/empty TE key is not routed");
            formal.onChunkUnload(); world.tiles.clear();
            client.receive(routes(900, 3, 0, 1));
            int played = client.played.size(); for (int i = 0; i < 21; i++) client.tick();
            check(client.played.size() == played, "Unloaded TE is removed before the next part");
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
    private static class CountingAnnouncer extends TileEntityAnnouncer {
        int starts, stops, dataReceives;
        @Override public void startAnnounce() { starts++; }
        @Override public void forceStop() { stops++; }
        @Override public void onDataReceived(Map<String, String> data, String sourcePos) {
            dataReceives++;
            super.onDataReceived(data, sourcePos);
        }
    }
    private static final class FakeTrain extends Entity implements TrainSnapshot {
        final boolean controlCar;
        final long formationId;
        final String name;
        int controlChecks, formationChecks, extractChecks;
        FakeTrain(World world, int entityId, boolean controlCar, long formationId, String name, double x, double z) {
            super(world);
            setEntityId(entityId);
            this.controlCar = controlCar;
            this.formationId = formationId;
            this.name = name;
            setSize(1, 1);
            setPosition(x, 0, z);
        }
        @Override protected void entityInit() {}
        @Override protected void readEntityFromNBT(NBTTagCompound nbt) {}
        @Override protected void writeEntityToNBT(NBTTagCompound nbt) {}
        @Override public boolean isControlCar() { controlChecks++; return controlCar; }
        @Override public long getFormationId() { formationChecks++; return formationId; }
        @Override public String extractData(String key, int type) {
            extractChecks++;
            return "name".equals(key) && type == 0 ? name : null;
        }
    }
    private static final class FakeTrainCompat implements TrainCompat {
        int wraps;
        @Override public String getId() { return "test"; }
        @Override public boolean isAvailable() { return true; }
        @Override public TrainSnapshot wrap(Entity entity) { wraps++; return entity instanceof FakeTrain ? (FakeTrain)entity : null; }
        @Override public boolean isInspectionTool(ItemStack stack) { return false; }
    }
    private static class CountingAwareness extends TileEntityAwarenessAnnouncer {
        int scheduled;
        @Override public void scheduleAfterDeparture() { scheduled++; }
    }
    private static class FixtureWorld extends World {
        int updates, entityScans;
        long time;
        final Map<Long, TileEntity> tiles = new HashMap<>();
        FixtureWorld() {
            super(new SaveHandlerMP(), "network-test", new WorldProviderSurface(), new WorldSettings(0, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT), new Profiler());
            loadedTileEntityList = new ArrayList() { @Override public Iterator iterator() { throw new AssertionError("Full TE scan"); } };
            loadedEntityList = new ArrayList() {
                @Override public Iterator iterator() { entityScans++; return super.iterator(); }
            };
        }
        void add(TileEntity tile, int x, int y, int z) {
            tile.setWorldObj(this); tile.xCoord = x; tile.yCoord = y; tile.zCoord = z;
            tiles.put(SpeakerRegistry.position(x, y, z), tile); tile.validate();
        }
        FakeTrain addTrain(int id, boolean control, long formation, String name, double x, double z) {
            FakeTrain train = new FakeTrain(this, id, control, formation, name, x, z);
            loadedEntityList.add(train);
            return train;
        }
        void nextTick() { time++; }
        @Override public long getTotalWorldTime() { return time; }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public List getEntitiesWithinAABB(Class type, AxisAlignedBB bounds) {
            throw new AssertionError("Detector performed a per-query World entity search");
        }
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
        int count(Class<?> type) {
            int count = 0;
            for (IMessage message : messages) if (type.isInstance(message)) count++;
            return count;
        }
        PacketSessionSpeakerRoutes routeFor(long sessionId) {
            PacketSessionSpeakerRoutes found = null;
            for (IMessage message : messages) if (message instanceof PacketSessionSpeakerRoutes
                && ((PacketSessionSpeakerRoutes)message).sessionId == sessionId)
                found = (PacketSessionSpeakerRoutes)message;
            return found;
        }
        void clear() { messages.clear(); players.clear(); }
    }
    private static class ReentrantDelivery extends RecordingDelivery {
        final FixtureWorld world;
        boolean armed, mutated;
        ReentrantDelivery(FixtureWorld world) { this.world = world; }
        @Override public void send(IMessage packet, EntityPlayerMP player) {
            super.send(packet, player);
            if (armed && !mutated && packet instanceof PacketSessionSpeakerRoutes) {
                mutated = true;
                Speaker speaker = new Speaker(); speaker.linkKey = "A"; world.add(speaker, 10, 0, 0);
            }
        }
    }
    private static class TestClient extends AnnounceManager {
        World world; int worldReads, ticks;
        PacketAnnounce receiveDuringStop;
        boolean overflowObservedDuringStop;
        int pendingObservedDuringStop = -1;
        final List<ISound> played = new ArrayList<>(); final Set<ISound> live = new HashSet<>(); final Set<Long> ended = new HashSet<>();
        final List<Integer> playedAtTicks = new ArrayList<>();
        @Override protected World currentWorld() { worldReads++; return world; }
        @Override protected void playSound(ISound sound) { played.add(sound); playedAtTicks.add(ticks); live.add(sound); }
        @Override protected void stopSound(ISound sound) {
            if (receiveDuringStop != null) {
                PacketAnnounce packet = receiveDuringStop;
                receiveDuringStop = null;
                overflowObservedDuringStop = overflowPending();
                receive(packet);
                pendingObservedDuringStop = pendingCount();
            }
            live.remove(sound);
        }
        @Override protected boolean inSpeakerRange(TileEntitySpeaker speaker) { return true; }
        @Override protected boolean inRange(int x, int y, int z, int range) { return true; }
        @Override protected void requestMissing(PacketMissingSpeakers packet) { missing.add(packet); }
        final List<PacketMissingSpeakers> missing = new ArrayList<>();
        @Override protected void finished(long id) { check(ended.add(id), "No duplicate completion acknowledgement"); }
        int pendingCount() { return pendingActionCount(); }
        boolean overflowPending() { return hasPendingOverflow(); }
        void tick() { ticks++; onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START)); }
    }

    private static class RoutingClient extends TestClient {
        double px, py, pz;
        @Override protected boolean inSpeakerRange(TileEntitySpeaker speaker) {
            return inRange(speaker.xCoord, speaker.yCoord, speaker.zCoord, speaker.range);
        }
        @Override protected boolean inRange(int x, int y, int z, int range) {
            double dx = px - (x + .5), dy = py - (y + .5), dz = pz - (z + .5);
            return dx*dx + dy*dy + dz*dz <= (double)range * range;
        }
    }
}
