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
                // START and OFF can arrive in one client tick; initialize before applying OFF.
                initializeDeparture(session);
                boolean wasOn = session.sequence.isOn();
                session.sequence.release();
                if (wasOn) session.releasedThisTick = true;
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

    private static class DeferredSound {
        final long target, expires;
        final ResourceLocation sound;
        final jp.me1han.sam.api.DepartureSequence.Channel channel;
        DeferredSound(long target, long expires, ResourceLocation sound, jp.me1han.sam.api.DepartureSequence.Channel channel) {
            this.target = target; this.expires = expires; this.sound = sound; this.channel = channel;
        }
    }

    public void receive(jp.me1han.sam.network.PacketSpeakerFallback packet) {
        pending.add(() -> {
            AnnounceSession session = activeSessions.get(packet.sessionId);
            if (session == null || !session.requestedMissing) return;
            for (jp.me1han.sam.network.PacketSpeakerFallback.Target target : packet.targets)
                session.fallback.put(target.position, target);
        });
    }

    private class AnnounceSession {
        final long sessionId;
        final long[] targets;
        final List<DeferredSound> deferred = new ArrayList<>();
        final Map<Long, jp.me1han.sam.network.PacketSpeakerFallback.Target> fallback = new java.util.HashMap<>();
        boolean requestedMissing;
        long unresolvedSince = -1;
        final String linkKey;
        final int priority;
        final boolean allowOverlap;
        final jp.me1han.sam.api.DepartureProgram departure;
        jp.me1han.sam.api.DepartureSequence sequence;
        boolean sequenceChangedThisTick;
        boolean releasedThisTick;
        final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
        String loopSound;
        boolean playLocalSound;
        int x, y, z;
        int waitTicks = 0;
        boolean isPlaying = true;
        boolean hasStartedPlayback = false;
        final List<ISound> activeSounds = new ArrayList<>();
        final Map<jp.me1han.sam.api.DepartureSequence.Channel, List<ISound>> departureSounds =
            new java.util.EnumMap<>(jp.me1han.sam.api.DepartureSequence.Channel.class);
        jp.me1han.sam.api.DepartureSequence.Channel playbackChannel;

        void trackSound(ISound sound) {
            if (playbackChannel == null) activeSounds.add(sound);
            else departureSounds.computeIfAbsent(playbackChannel, key -> new ArrayList<>()).add(sound);
        }

        void stopChannel(jp.me1han.sam.api.DepartureSequence.Channel channel) {
            deferred.removeIf(sound -> sound.channel == channel);
            List<ISound> sounds = departureSounds.remove(channel);
            if (sounds != null) for (ISound sound : sounds)
                stopSound(sound);
        }

        AnnounceSession(PacketAnnounce msg) {
            this.sessionId = msg.sessionId;
            this.targets = msg.targets.clone();
            this.linkKey = normalizeKeyStatic(msg.linkKey);
            this.priority = msg.priority;
            this.allowOverlap = msg.allowOverlap;
            this.departure = msg instanceof jp.me1han.sam.network.PacketDepartureStart
                ? ((jp.me1han.sam.network.PacketDepartureStart)msg).departure : null;
            this.playLocalSound = msg.playLocalSound;
            this.x = msg.x;
            this.y = msg.y;
            this.z = msg.z;

            if (msg.startMelo != null && !msg.startMelo.isEmpty()) {
                this.queue.add(msg.startMelo);
            }
            if (msg.bodySounds != null) {
                for (String s : msg.bodySounds) {
                    if (s != null && !s.isEmpty()) {
                        this.queue.add(s);
                    }
                }
            }
            this.loopSound = (msg.arrMelo != null && !msg.arrMelo.isEmpty()) ? msg.arrMelo : null;
        }

        void stop() {
            this.isPlaying = false;
            if (sequence != null) sequence.cancel();
            stopSounds();
            queue.clear();
            deferred.clear();
            fallback.clear();
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

        String key = normalizeKey(msg.linkKey);
        long sessionKey = msg.sessionId;
        if (activeSessions.containsKey(sessionKey)) return;

        // Decide whether the new session may start before mutating any active session.
        // This keeps a rejected lower-priority request from stopping unrelated audio.
        for (Map.Entry<Long, AnnounceSession> entry : activeSessions.entrySet()) {
            AnnounceSession existing = entry.getValue();
            if (!key.equals(existing.linkKey)) {
                continue;
            }

            if (existing.priority > msg.priority && !msg.allowOverlap
                && msg.priority != PacketAnnounce.PRIORITY_AWARENESS) {
                finished(msg.sessionId);
                return;
            }
        }

        for (Map.Entry<Long, AnnounceSession> entry : activeSessions.entrySet()) {
            AnnounceSession existing = entry.getValue();
            if (!key.equals(existing.linkKey)) {
                continue;
            }
            boolean interruptLower = existing.priority < msg.priority && !existing.allowOverlap
                && (existing.priority != PacketAnnounce.PRIORITY_AWARENESS || existing.hasStartedPlayback);
            if (existing.priority == msg.priority || interruptLower) {
                existing.stop();
                activeSessions.remove(entry.getKey(), existing);
                finished(existing.sessionId);
            }
        }

        activeSessions.put(sessionKey, new AnnounceSession(msg));

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

            if (isBlockedByHigherPriority(session)) {
                continue;
            }

            retryDeferred(session, world);

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

            String nextSound = session.queue.poll();

            if (nextSound != null) {
                playInSession(session, nextSound);
                session.waitTicks = getSoundTicks(nextSound);
            } else if (session.loopSound != null) {
                playInSession(session, session.loopSound);
                session.waitTicks = getSoundTicks(session.loopSound);
            } else {
                session.stop();
                finished(session.sessionId);
                it.remove();
            }
        }
    }

    private void initializeDeparture(final AnnounceSession session) {
        if (session.sequence != null) return;
        session.sequenceChangedThisTick = true;
        session.sequence = new jp.me1han.sam.api.DepartureSequence(session.departure,
            new jp.me1han.sam.api.DepartureSequence.Output() {
                public void play(jp.me1han.sam.api.DepartureSequence.Channel channel, String sound) {
                    session.playbackChannel = channel;
                    try { playInSession(session, sound); }
                    finally { session.playbackChannel = null; }
                }
                public void stop(jp.me1han.sam.api.DepartureSequence.Channel channel) { session.stopChannel(channel); }
                public void finished() { session.isPlaying = false; }
            });
    }

    private int getSoundTicks(String soundId) {
        if (soundId == null || soundId.isEmpty()) {
            return 20;
        }
        Integer ticks = AnnouncePackLoader.soundTicks.get(soundId);
        return (ticks != null) ? ticks : 20;
    }

    private boolean isBlockedByHigherPriority(AnnounceSession session) {
        if (session.allowOverlap || session.priority != PacketAnnounce.PRIORITY_AWARENESS) {
            return false;
        }

        for (AnnounceSession other : activeSessions.values()) {
            if (other != session && other.isPlaying && session.linkKey.equals(other.linkKey)
                && other.priority > session.priority) {
                return true;
            }
        }
        return false;
    }

    private void playInSession(AnnounceSession session, String soundId) {
        if (session == null || soundId == null || soundId.isEmpty()) {
            return;
        }

        if (session.departure == null) {
            // Retain sound handles until the sequence advances, including late TE playback.
            for (ISound sound : session.activeSounds) stopSound(sound);
            session.activeSounds.clear();
            session.deferred.clear();
        }
        session.hasStartedPlayback = true;
        try {
            ResourceLocation res = new ResourceLocation(soundId);
            World world = currentWorld();
            if (world == null) {
                return;
            }

            for (long target : session.targets) {
                if (!playTarget(session, res, target, world)) {
                    session.deferred.add(new DeferredSound(target, clientTick + getSoundTicks(soundId),
                        res, session.playbackChannel));
                }
            }
            if (session.playLocalSound) playLocalSound(res, session);

        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Session Playback Error: " + soundId, e);
        }
    }

    /** Only supplied coordinates are consulted; missing TE/settings are retried during this sound. */
    private boolean playTarget(AnnounceSession session, ResourceLocation sound, long target, World world) {
        int x = jp.me1han.sam.SpeakerRegistry.x(target), y = jp.me1han.sam.SpeakerRegistry.y(target), z = jp.me1han.sam.SpeakerRegistry.z(target);
        net.minecraft.tileentity.TileEntity tile = world.blockExists(x, y, z) ? world.getTileEntity(x, y, z) : null;
        if (tile instanceof TileEntitySpeaker && !tile.isInvalid()) {
            TileEntitySpeaker speaker = (TileEntitySpeaker)tile;
            if (speaker.isClientConfigSynced()) {
                session.fallback.remove(target);
                if (session.linkKey.equals(normalizeKey(speaker.linkKey))) {
                    playSoundAtSpeaker(sound, session, speaker);
                }
                return true; // A synchronized empty/mismatched key is authoritative.
            }
            // The TE instance may precede its S35 description; keep fallback/retry active.
        }
        jp.me1han.sam.network.PacketSpeakerFallback.Target settings = session.fallback.get(target);
        if (settings == null) return false;
        playAtCoordinates(sound, session, x, y, z, settings.range, settings.volume);
        return true;
    }

    private void retryDeferred(AnnounceSession session, World world) {
        Iterator<DeferredSound> it = session.deferred.iterator();
        while (it.hasNext()) {
            DeferredSound deferred = it.next();
            if (clientTick >= deferred.expires) { it.remove(); continue; }
            session.playbackChannel = deferred.channel;
            try {
                if (playTarget(session, deferred.sound, deferred.target, world)) it.remove();
            } finally { session.playbackChannel = null; }
        }
        if (!session.deferred.isEmpty() && !session.requestedMissing) {
            if (session.unresolvedSince < 0) session.unresolvedSince = clientTick;
            // Allow TE descriptions to catch up without delaying the sequence clock.
            if (clientTick - session.unresolvedSince >= 2) {
                java.util.Set<Long> missing = new java.util.LinkedHashSet<>();
                for (DeferredSound sound : session.deferred) missing.add(sound.target);
                long[] positions = new long[missing.size()]; int index = 0;
                for (long position : missing) positions[index++] = position;
                session.requestedMissing = true;
                requestMissing(new jp.me1han.sam.network.PacketMissingSpeakers(session.sessionId, positions));
            }
        }
    }

    private void playSoundAtSpeaker(ResourceLocation res, AnnounceSession session, TileEntitySpeaker speaker) {
        if (inSpeakerRange(speaker)) playAtCoordinates(res, session, speaker.xCoord, speaker.yCoord, speaker.zCoord, speaker.range, speaker.volume);
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
        if (key == null) {
            return "";
        }
        return key.trim();
    }

    private static String normalizeKeyStatic(String key) {
        if (key == null) {
            return "";
        }
        return key.trim();
    }

}
