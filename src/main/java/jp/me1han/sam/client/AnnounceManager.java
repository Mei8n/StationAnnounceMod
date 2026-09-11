package jp.me1han.sam.client;

import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.network.PacketAnnounce;
import jp.me1han.sam.render.TileEntitySpeaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AnnounceManager {
    public static final AnnounceManager INSTANCE = new AnnounceManager();

    private final Map<Long, AnnounceSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();
    private World sessionWorld;
    private long clientTick;

    protected World currentWorld() { return Minecraft.getMinecraft().theWorld; }
    protected void playSound(ISound sound) { Minecraft.getMinecraft().getSoundHandler().playSound(sound); }
    protected void stopSound(ISound sound) { Minecraft.getMinecraft().getSoundHandler().stopSound(sound); }
    protected boolean inSpeakerRange(TileEntitySpeaker speaker) {
        return inRange(speaker.xCoord, speaker.yCoord, speaker.zCoord, speaker.range);
    }
    protected boolean inRange(int x, int y, int z, int range) {
        return Minecraft.getMinecraft().thePlayer != null
            && Minecraft.getMinecraft().thePlayer.getDistanceSq(x+.5, y+.5, z+.5) <= (double)range * range;
    }
    protected void requestMissing(jp.me1han.sam.network.PacketMissingSpeakers packet) {
        NetworkHandler.INSTANCE.sendToServer(packet);
    }

    public void receive(PacketAnnounce packet) {
        pending.add(() -> startAnnounce(packet));
    }

    public void receive(jp.me1han.sam.network.PacketDepartureControl packet) {
        pending.add(() -> {
            long key = packet.sessionId;
            AnnounceSession session = activeSessions.get(key);
            if (session == null || session.departure == null) return;
            if (packet.cancel) {
                session.stop();
                activeSessions.remove(key);
            } else {
                // Preserve an early OFF, but do not initialize playback until the
                // authoritative initial routing snapshot is complete.
                session.departureReleased = true;
                initializeDeparture(session);
                releaseDeparture(session);
            }
        });
    }

    public void receive(jp.me1han.sam.network.PacketAnnounceStop packet) {
        pending.add(() -> {
            if (packet.sessionId == 0) stopAnnounce();
            else {
                AnnounceSession session = activeSessions.remove(packet.sessionId);
                if (session != null) session.stop();
            }
        });
    }

    private static class AnnouncePart {
        final String sound;
        final int durationTicks;
        AnnouncePart(String sound, int durationTicks) {
            this.sound = sound;
            this.durationTicks = durationTicks;
        }
        boolean isInterval() { return sound == null || sound.isEmpty(); }
    }

    public void receive(jp.me1han.sam.network.PacketSpeakerFallback packet) {
        // Legacy packet retained for wire compatibility. Dynamic sessions never
        // request or trust coordinate settings outside synchronized client TEs.
    }

    public void receive(final jp.me1han.sam.network.PacketSessionSpeakerRoutes packet) {
        pending.add(() -> {
            AnnounceSession session = activeSessions.get(packet.sessionId);
            if (session != null) session.acceptRoutes(packet);
        });
    }

    private class AnnounceSession {
        final long sessionId;
        final String linkKey;
        final int priority;
        final boolean allowOverlap;
        final jp.me1han.sam.api.DepartureProgram departure;
        jp.me1han.sam.api.DepartureSequence sequence;
        boolean sequenceChangedThisTick;
        boolean releasedThisTick;
        final ConcurrentLinkedQueue<AnnouncePart> queue = new ConcurrentLinkedQueue<>();
        final String startMelo;
        final int startMeloTicks;
        final List<String> bodySounds;
        final List<Integer> bodyPartTicks;
        int repeatsRemaining;
        String loopSound;
        int loopTicks;
        boolean playLocalSound;
        int x, y, z;
        int waitTicks = 0;
        boolean isPlaying = true;
        boolean hasStartedPlayback = false;
        final List<ISound> activeSounds = new ArrayList<>();
        final Map<jp.me1han.sam.api.DepartureSequence.Channel, List<ISound>> departureSounds =
            new java.util.EnumMap<>(jp.me1han.sam.api.DepartureSequence.Channel.class);
        jp.me1han.sam.api.DepartureSequence.Channel playbackChannel;
        Map<Long, jp.me1han.sam.network.PacketSessionSpeakerRoutes.Target> routes = new HashMap<>();
        long routeRevision;
        RouteAssembly routeAssembly;
        long audibleTick = Long.MIN_VALUE;
        boolean audibleThisTick;
        boolean priorityArbitrated;
        boolean departureReleased;

        final class RouteAssembly {
            final long revision;
            final int chunkCount;
            final Map<Integer, List<jp.me1han.sam.network.PacketSessionSpeakerRoutes.Target>> chunks = new HashMap<>();

            RouteAssembly(long revision, int chunkCount) {
                this.revision = revision;
                this.chunkCount = chunkCount;
            }
        }

        void trackSound(ISound sound) {
            if (playbackChannel == null) activeSounds.add(sound);
            else departureSounds.computeIfAbsent(playbackChannel, key -> new ArrayList<>()).add(sound);
        }

        void stopChannel(jp.me1han.sam.api.DepartureSequence.Channel channel) {
            List<ISound> sounds = departureSounds.remove(channel);
            if (sounds != null) for (ISound sound : sounds)
                stopSound(sound);
        }

        void acceptRoutes(jp.me1han.sam.network.PacketSessionSpeakerRoutes packet) {
            if (packet.revision <= routeRevision) return;
            if (routeAssembly == null || packet.revision > routeAssembly.revision) {
                routeAssembly = new RouteAssembly(packet.revision, packet.chunkCount);
            }
            if (packet.revision != routeAssembly.revision || packet.chunkCount != routeAssembly.chunkCount
                || routeAssembly.chunks.containsKey(packet.chunkIndex)) return;
            routeAssembly.chunks.put(packet.chunkIndex,
                new ArrayList<jp.me1han.sam.network.PacketSessionSpeakerRoutes.Target>(packet.targets));
            if (routeAssembly.chunks.size() != routeAssembly.chunkCount) return;

            Map<Long, jp.me1han.sam.network.PacketSessionSpeakerRoutes.Target> complete = new HashMap<>();
            for (int i = 0; i < routeAssembly.chunkCount; i++) {
                List<jp.me1han.sam.network.PacketSessionSpeakerRoutes.Target> chunk = routeAssembly.chunks.get(i);
                if (chunk == null) return;
                for (jp.me1han.sam.network.PacketSessionSpeakerRoutes.Target target : chunk)
                    complete.put(target.position, target);
            }
            routes = complete;
            routeRevision = routeAssembly.revision;
            routeAssembly = null;
            audibleTick = Long.MIN_VALUE;
            if (!priorityArbitrated) arbitrateInitialPriority(this);
        }

        boolean routingReady() {
            return routeRevision > 0 && priorityArbitrated;
        }

        AnnounceSession(PacketAnnounce msg) {
            this.sessionId = msg.sessionId;
            this.linkKey = normalizeKeyStatic(msg.linkKey);
            this.priority = msg.priority;
            this.allowOverlap = msg.allowOverlap;
            this.departure = msg instanceof jp.me1han.sam.network.PacketDepartureStart
                ? ((jp.me1han.sam.network.PacketDepartureStart)msg).departure : null;
            this.startMelo = msg.startMelo;
            this.startMeloTicks = msg.startMeloTicks;
            this.bodySounds = msg.bodySounds == null
                ? java.util.Collections.<String>emptyList() : new ArrayList<>(msg.bodySounds);
            this.bodyPartTicks = msg.bodyPartTicks == null
                ? java.util.Collections.<Integer>emptyList() : new ArrayList<>(msg.bodyPartTicks);
            this.repeatsRemaining = departure == null
                ? jp.me1han.sam.api.AnnounceData.normalizeRepeatCount(msg.repeatCount) : 0;
            this.playLocalSound = msg.playLocalSound;
            this.x = msg.x;
            this.y = msg.y;
            this.z = msg.z;

            enqueueNextRepeat();
            this.loopSound = (msg.arrMelo != null && !msg.arrMelo.isEmpty()) ? msg.arrMelo : null;
            this.loopTicks = msg.arrMeloTicks;
        }

        boolean enqueueNextRepeat() {
            while (repeatsRemaining > 0) {
                repeatsRemaining--;
                if (startMelo != null && !startMelo.isEmpty()) queue.add(new AnnouncePart(startMelo, startMeloTicks));
                for (int i = 0; i < bodySounds.size(); i++) {
                    String sound = bodySounds.get(i);
                    int duration = bodyPartTicks.get(i);
                    queue.add(new AnnouncePart(sound, duration));
                }
                if (!queue.isEmpty()) return true;
            }
            return false;
        }

        void stop() {
            this.isPlaying = false;
            if (sequence != null) sequence.cancel();
            stopSounds();
            queue.clear();
            routes.clear();
            routeAssembly = null;
        }

        void stopSounds() {
            for (jp.me1han.sam.api.DepartureSequence.Channel channel : jp.me1han.sam.api.DepartureSequence.Channel.values())
                stopChannel(channel);
            for (ISound s : activeSounds) {
                if (s != null) {
                    stopSound(s);
                }
            }
            activeSounds.clear();
        }
    }

    private void startAnnounce(PacketAnnounce msg) {
        if (msg == null || msg.linkKey == null) {
            return;
        }
        if (!(msg instanceof jp.me1han.sam.network.PacketDepartureStart)) {
            try { msg.validateTiming(); }
            catch (IllegalArgumentException invalid) {
                StationAnnounceModCore.logger.error("[SAM] Rejected START without valid canonical timing: " + invalid.getMessage());
                finished(msg.sessionId);
                return;
            }
        }

        long sessionKey = msg.sessionId;
        if (activeSessions.containsKey(sessionKey)) return;
        AnnounceSession candidate = new AnnounceSession(msg);
        activeSessions.put(sessionKey, candidate);
    }

    /** Apply START arbitration only after its authoritative initial route snapshot is complete. */
    private void arbitrateInitialPriority(AnnounceSession candidate) {
        if (candidate.priorityArbitrated || activeSessions.get(candidate.sessionId) != candidate) return;
        candidate.priorityArbitrated = true;
        if (!isCurrentlyAudible(candidate)) return;

        for (AnnounceSession existing : activeSessions.values()) {
            if (existing == candidate || !candidate.linkKey.equals(existing.linkKey)
                || !existing.routingReady() || !isCurrentlyAudible(existing)) continue;
            if (existing.priority > candidate.priority && !candidate.allowOverlap
                && candidate.priority != PacketAnnounce.PRIORITY_AWARENESS) {
                activeSessions.remove(candidate.sessionId, candidate);
                candidate.stop();
                finished(candidate.sessionId);
                return;
            }
        }
        for (Map.Entry<Long, AnnounceSession> entry : activeSessions.entrySet()) {
            AnnounceSession existing = entry.getValue();
            if (existing == candidate || !candidate.linkKey.equals(existing.linkKey)
                || !existing.routingReady() || !isCurrentlyAudible(existing)) continue;
            boolean interruptLower = existing.priority < candidate.priority && !existing.allowOverlap
                && (existing.priority != PacketAnnounce.PRIORITY_AWARENESS || existing.hasStartedPlayback);
            if (existing.priority == candidate.priority || interruptLower) {
                existing.stop();
                activeSessions.remove(entry.getKey(), existing);
                finished(existing.sessionId);
            }
        }
    }

    public void stopAnnounce() {
        for (AnnounceSession session : activeSessions.values()) session.stop();
        activeSessions.clear();
    }

    protected void finished(long sessionId) {
        NetworkHandler.INSTANCE.sendToServer(new jp.me1han.sam.network.PacketSessionFinished(sessionId));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        World world = currentWorld();
        if (sessionWorld != world) {
            for (AnnounceSession session : activeSessions.values()) session.stop();
            activeSessions.clear();
            if (sessionWorld != null) pending.clear();
            ClientSpeakerRegistry.clear(sessionWorld);
            sessionWorld = world;
        }
        if (world == null) { pending.clear(); return; }
        clientTick++;
        Runnable action;
        while ((action = pending.poll()) != null) action.run();

        Iterator<Map.Entry<Long, AnnounceSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, AnnounceSession> entry = it.next();
            AnnounceSession session = entry.getValue();

            if (!session.isPlaying) {
                finished(session.sessionId);
                it.remove();
                continue;
            }

            if (!session.routingReady()) {
                continue;
            }

            if (isBlockedByHigherPriority(session)) {
                continue;
            }

            if (session.departure != null) {
                if (session.sequence == null) initializeDeparture(session);
                else if (!session.sequenceChangedThisTick) session.sequence.tick(session.releasedThisTick);
                session.sequenceChangedThisTick = false;
                session.releasedThisTick = false;
                if (session.sequence.isFinished()) { finished(session.sessionId); it.remove(); }
                continue;
            }

            if (session.waitTicks > 0) {
                session.waitTicks--;
                continue;
            }

            AnnouncePart nextPart = session.queue.poll();
            if (nextPart == null && session.enqueueNextRepeat()) nextPart = session.queue.poll();

            if (nextPart != null) {
                if (nextPart.isInterval()) {
                    for (ISound sound : session.activeSounds) stopSound(sound);
                    session.activeSounds.clear();
                    // This tick is already the first silent tick.
                    session.waitTicks = nextPart.durationTicks - 1;
                } else {
                    playInSession(session, nextPart.sound, nextPart.durationTicks);
                    session.waitTicks = nextPart.durationTicks;
                }
            } else if (session.loopSound != null) {
                playInSession(session, session.loopSound, session.loopTicks);
                session.waitTicks = session.loopTicks;
            } else {
                session.stop();
                finished(session.sessionId);
                it.remove();
            }
        }
    }

    private void initializeDeparture(final AnnounceSession session) {
        if (session.sequence != null || !session.routingReady()) return;
        session.sequenceChangedThisTick = true;
        session.sequence = new jp.me1han.sam.api.DepartureSequence(session.departure,
            new jp.me1han.sam.api.DepartureSequence.Output() {
                public void play(jp.me1han.sam.api.DepartureSequence.Channel channel, String sound) {
                    session.playbackChannel = channel;
                    try { playDepartureInSession(session, sound); }
                    finally { session.playbackChannel = null; }
                }
                public void stop(jp.me1han.sam.api.DepartureSequence.Channel channel) { session.stopChannel(channel); }
                public void finished() { session.isPlaying = false; }
            });
        releaseDeparture(session);
    }

    private void releaseDeparture(AnnounceSession session) {
        if (session.sequence == null || !session.departureReleased) return;
        boolean wasOn = session.sequence.isOn();
        session.sequence.release();
        if (wasOn) session.releasedThisTick = true;
    }

    private boolean isBlockedByHigherPriority(AnnounceSession session) {
        if (session.allowOverlap || session.priority != PacketAnnounce.PRIORITY_AWARENESS) {
            return false;
        }
        if (!isCurrentlyAudible(session)) return false;

        for (AnnounceSession other : activeSessions.values()) {
            if (other != session && other.isPlaying && other.routingReady()
                && session.linkKey.equals(other.linkKey)
                && other.priority > session.priority && isCurrentlyAudible(other)) {
                return true;
            }
        }
        return false;
    }

    private void playDepartureInSession(AnnounceSession session, String soundId) {
        // Only Departure uses this overload; its sequence clock is already server-resolved.
        Integer ticks = AnnouncePackLoader.soundTicks.get(soundId);
        playInSession(session, soundId, ticks != null ? ticks : 20);
    }

    private void playInSession(AnnounceSession session, String soundId, int durationTicks) {
        if (session == null || soundId == null || soundId.isEmpty()) {
            return;
        }

        if (session.departure == null) {
            // Retain sound handles until the sequence advances.
            for (ISound sound : session.activeSounds) stopSound(sound);
            session.activeSounds.clear();
        }
        try {
            ResourceLocation res = new ResourceLocation(soundId);
            World world = currentWorld();
            if (world == null) {
                return;
            }

            if (isPlaybackSuppressed(session)) return;
            boolean played = false;
            if (session.routeRevision > 0) {
                for (jp.me1han.sam.network.PacketSessionSpeakerRoutes.Target target : session.routes.values()) {
                    TileEntitySpeaker speaker = ClientSpeakerRegistry.at(world, target.position);
                    if (speaker != null) {
                        if (session.linkKey.equals(normalizeKey(speaker.linkKey))
                            && playSoundAtSpeaker(res, session, speaker)) {
                            played = true;
                        }
                    } else if (playDescriptor(res, session, target)) {
                        played = true;
                    }
                }
            } else {
                Collection<TileEntitySpeaker> speakers = ClientSpeakerRegistry.findByKey(world, session.linkKey);
                for (TileEntitySpeaker speaker : speakers) {
                    if (playSoundAtSpeaker(res, session, speaker)) {
                        played = true;
                    }
                }
            }
            if (session.playLocalSound
                && inRange(session.x, session.y, session.z, (int)jp.me1han.sam.network.ServerSessions.LOCAL_RANGE)) {
                playLocalSound(res, session);
                played = true;
            }
            if (played) session.hasStartedPlayback = true;

        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Session Playback Error: " + soundId, e);
        }
    }

    private boolean isCurrentlyAudible(AnnounceSession session) {
        if (session.audibleTick == clientTick) return session.audibleThisTick;
        World world = currentWorld();
        boolean audible = false;
        if (world == null) return cacheAudible(session, false);
        if (session.playLocalSound
            && inRange(session.x, session.y, session.z, (int)jp.me1han.sam.network.ServerSessions.LOCAL_RANGE))
            return cacheAudible(session, true);
        if (session.routeRevision > 0) {
            for (jp.me1han.sam.network.PacketSessionSpeakerRoutes.Target target : session.routes.values()) {
                TileEntitySpeaker speaker = ClientSpeakerRegistry.at(world, target.position);
                if (speaker != null) {
                    if (session.linkKey.equals(normalizeKey(speaker.linkKey)) && validAudibleSpeaker(speaker)) {
                        audible = true;
                        break;
                    }
                } else if (validAudibleDescriptor(target)) {
                    audible = true;
                    break;
                }
            }
        } else {
            for (TileEntitySpeaker speaker : ClientSpeakerRegistry.findByKey(world, session.linkKey))
                if (validAudibleSpeaker(speaker)) { audible = true; break; }
        }
        return cacheAudible(session, audible);
    }

    private boolean cacheAudible(AnnounceSession session, boolean audible) {
        session.audibleTick = clientTick;
        session.audibleThisTick = audible;
        return audible;
    }

    private boolean isPlaybackSuppressed(AnnounceSession session) {
        if (session.allowOverlap) return false;
        for (AnnounceSession other : activeSessions.values()) {
            if (other == session || !other.isPlaying || !other.routingReady()
                || !session.linkKey.equals(other.linkKey)
                || !isCurrentlyAudible(other)) continue;
            if (other.priority > session.priority
                || (other.priority == session.priority && other.sessionId > session.sessionId)) return true;
        }
        return false;
    }

    private boolean validAudibleSpeaker(TileEntitySpeaker speaker) {
        return speaker != null && !speaker.isInvalid() && speaker.isClientConfigSynced()
            && jp.me1han.sam.network.PacketLimits.speaker(speaker.range, speaker.volume)
            && speaker.volume > 0 && inSpeakerRange(speaker);
    }

    private boolean validAudibleDescriptor(jp.me1han.sam.network.PacketSessionSpeakerRoutes.Target target) {
        return target != null && jp.me1han.sam.network.PacketLimits.speaker(target.range, target.volume)
            && target.volume > 0 && inRange(jp.me1han.sam.SpeakerRegistry.x(target.position),
                jp.me1han.sam.SpeakerRegistry.y(target.position), jp.me1han.sam.SpeakerRegistry.z(target.position), target.range);
    }

    private boolean playDescriptor(ResourceLocation res, AnnounceSession session,
        jp.me1han.sam.network.PacketSessionSpeakerRoutes.Target target) {
        if (!validAudibleDescriptor(target)) return false;
        playAtCoordinates(res, session, jp.me1han.sam.SpeakerRegistry.x(target.position),
            jp.me1han.sam.SpeakerRegistry.y(target.position), jp.me1han.sam.SpeakerRegistry.z(target.position),
            target.range, target.volume);
        return true;
    }

    private boolean playSoundAtSpeaker(ResourceLocation res, AnnounceSession session, TileEntitySpeaker speaker) {
        if (!validAudibleSpeaker(speaker)) return false;
        playAtCoordinates(res, session, speaker.xCoord, speaker.yCoord, speaker.zCoord, speaker.range, speaker.volume);
        return true;
    }

    private void playAtCoordinates(ResourceLocation res, AnnounceSession session, int x, int y, int z, int range, float volume) {
        try {
            if (!jp.me1han.sam.network.PacketLimits.speaker(range, volume) || volume <= 0 || !inRange(x, y, z, range)) {
                return;
            }

            float vol = (range / 16.0F) * volume;
            vol = Math.max(0.0F, Math.min(2.0F, vol)); // Clamp to reasonable range

            PositionedSoundRecord psr = new PositionedSoundRecord(res, vol, 1.0F,
                (float) x + 0.5F, (float) y + 0.5F, (float) z + 0.5F);
            playSound(psr);
            session.trackSound(psr);
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Failed to play sound at speaker", e);
        }
    }

    private void playLocalSound(ResourceLocation res, AnnounceSession session) {
        try {
            PositionedSoundRecord psr = new PositionedSoundRecord(res, 1.0F, 1.0F,
                (float) session.x + 0.5F, (float) session.y + 0.5F, (float) session.z + 0.5F);
            playSound(psr);
            session.trackSound(psr);
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Failed to play local sound", e);
        }
    }

    private String normalizeKey(String key) {
        return jp.me1han.sam.link.LinkKey.normalize(key);
    }

    private static String normalizeKeyStatic(String key) {
        return jp.me1han.sam.link.LinkKey.normalize(key);
    }

}
