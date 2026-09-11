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

        void trackSound(ISound sound) {
            if (playbackChannel == null) activeSounds.add(sound);
            else departureSounds.computeIfAbsent(playbackChannel, key -> new ArrayList<>()).add(sound);
        }

        void stopChannel(jp.me1han.sam.api.DepartureSequence.Channel channel) {
            List<ISound> sounds = departureSounds.remove(channel);
            if (sounds != null) for (ISound sound : sounds)
                stopSound(sound);
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

        String key = normalizeKey(msg.linkKey);
        long sessionKey = msg.sessionId;
        if (activeSessions.containsKey(sessionKey)) return;
        AnnounceSession candidate = new AnnounceSession(msg);
        boolean candidateAudible = isCurrentlyAudible(candidate);

        // Decide whether the new session may start before mutating any active session.
        // This keeps a rejected lower-priority request from stopping unrelated audio.
        for (Map.Entry<Long, AnnounceSession> entry : activeSessions.entrySet()) {
            AnnounceSession existing = entry.getValue();
            if (!candidateAudible || !key.equals(existing.linkKey) || !isCurrentlyAudible(existing)) {
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
            if (!candidateAudible || !key.equals(existing.linkKey) || !isCurrentlyAudible(existing)) {
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

        activeSessions.put(sessionKey, candidate);

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
        if (session.sequence != null) return;
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
    }

    private boolean isBlockedByHigherPriority(AnnounceSession session) {
        if (session.allowOverlap || session.priority != PacketAnnounce.PRIORITY_AWARENESS) {
            return false;
        }
        if (!isCurrentlyAudible(session)) return false;

        for (AnnounceSession other : activeSessions.values()) {
            if (other != session && other.isPlaying && session.linkKey.equals(other.linkKey)
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
            int sourceCount = 0;
            Collection<TileEntitySpeaker> speakers = ClientSpeakerRegistry.findByKey(world, session.linkKey);
            for (TileEntitySpeaker speaker : speakers) {
                if (sourceCount == jp.me1han.sam.network.PacketLimits.SESSION_TARGETS) break;
                if (playSoundAtSpeaker(res, session, speaker)) {
                    sourceCount++;
                    played = true;
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
        World world = currentWorld();
        if (world == null) return false;
        if (session.playLocalSound
            && inRange(session.x, session.y, session.z, (int)jp.me1han.sam.network.ServerSessions.LOCAL_RANGE)) return true;
        for (TileEntitySpeaker speaker : ClientSpeakerRegistry.findByKey(world, session.linkKey))
            if (validAudibleSpeaker(speaker)) return true;
        return false;
    }

    private boolean isPlaybackSuppressed(AnnounceSession session) {
        if (session.allowOverlap) return false;
        for (AnnounceSession other : activeSessions.values()) {
            if (other == session || !other.isPlaying || !session.linkKey.equals(other.linkKey)
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
