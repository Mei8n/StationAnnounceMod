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

    private final Map<String, AnnounceSession> activeSessions = new ConcurrentHashMap<>();

    private static class AnnounceSession {
        final String linkKey;
        final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
        final List<PacketAnnounce.SpeakerData> serverSpeakers;
        final int serverTotalSpeakers;
        final String serverSampleKeys;
        String loopSound;
        boolean playLocalSound;
        int x, y, z;
        int waitTicks = 0;
        boolean isPlaying = true;
        final List<ISound> activeSounds = new ArrayList<>();

        // Speakerキャッシュ: セッション開始時の1回だけスキャン
        List<TileEntitySpeaker> cachedSpeakers;
        boolean speakersInitialized = false;
        boolean speakerCheckLogged = false;
        boolean noMatchLogged = false;

        AnnounceSession(PacketAnnounce msg) {
            this.linkKey = normalizeKeyStatic(msg.linkKey);
            this.playLocalSound = msg.playLocalSound;
            this.x = msg.x;
            this.y = msg.y;
            this.z = msg.z;
            this.serverSpeakers = msg.speakers != null ? new ArrayList<PacketAnnounce.SpeakerData>(msg.speakers) : new ArrayList<PacketAnnounce.SpeakerData>();
            this.serverTotalSpeakers = msg.serverTotalSpeakers;
            this.serverSampleKeys = msg.serverSampleKeys != null ? msg.serverSampleKeys : "";

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
            for (ISound s : activeSounds) {
                if (s != null) {
                    Minecraft.getMinecraft().getSoundHandler().stopSound(s);
                }
            }
            activeSounds.clear();
            queue.clear();
        }
    }

    public void startAnnounce(PacketAnnounce msg) {
        if (msg == null || msg.linkKey == null) {
            return;
        }

        String key = normalizeKey(msg.linkKey);

        AnnounceSession existingSession = activeSessions.get(key);
        if (existingSession != null) {
            existingSession.stop();
        }

        activeSessions.put(key, new AnnounceSession(msg));

        // First sound for debug output
        String firstSound = getFirstSound(msg);

        // Send debug event (will be processed server-side)
        try {
            NetworkHandler.INSTANCE.sendToServer(new jp.me1han.sam.network.PacketDebugAnnounceEvent("START", key, firstSound, 0, msg.playLocalSound));
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Failed to send debug start event", e);
        }
    }

    public void stopAnnounce(String linkKey) {
        String normalizedKey = normalizeKey(linkKey);

        if (PacketAnnounce.GLOBAL_STOP_KEY.equals(normalizedKey) || normalizedKey.isEmpty()) {
            // Stop all sessions
            List<AnnounceSession> sessionsToStop = new ArrayList<>(activeSessions.values());
            for (AnnounceSession session : sessionsToStop) {
                try {
                    NetworkHandler.INSTANCE.sendToServer(new jp.me1han.sam.network.PacketDebugAnnounceEvent("STOP", session.linkKey, "", 0, session.playLocalSound));
                } catch (Exception e) {
                    StationAnnounceModCore.logger.error("[SAM] Failed to send debug stop event", e);
                }
                session.stop();
            }
            activeSessions.clear();
        } else {
            AnnounceSession session = activeSessions.remove(normalizedKey);
            if (session != null) {
                try {
                    NetworkHandler.INSTANCE.sendToServer(new jp.me1han.sam.network.PacketDebugAnnounceEvent("STOP", normalizedKey, "", 0, session.playLocalSound));
                } catch (Exception e) {
                    StationAnnounceModCore.logger.error("[SAM] Failed to send debug stop event", e);
                }
                session.stop();
            }
        }
    }

    public void stopAnnounce() {
        this.stopAnnounce(null);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || activeSessions.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<String, AnnounceSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AnnounceSession> entry = it.next();
            AnnounceSession session = entry.getValue();

            if (!session.isPlaying) {
                it.remove();
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
                session.isPlaying = false;
                it.remove();
            }
        }
    }

    private int getSoundTicks(String soundId) {
        if (soundId == null || soundId.isEmpty()) {
            return 20;
        }
        Integer ticks = AnnouncePackLoader.soundTicks.get(soundId);
        return (ticks != null) ? ticks : 20;
    }

    private void playInSession(AnnounceSession session, String soundId) {
        if (session == null || soundId == null || soundId.isEmpty()) {
            return;
        }

        session.activeSounds.clear();
        try {
            ResourceLocation res = new ResourceLocation(soundId);
            World world = Minecraft.getMinecraft().theWorld;
            if (world == null) {
                return;
            }

            if (!session.speakersInitialized) {
                initializeSpeakerCache(session, world);
                session.speakersInitialized = true;
            }

            int serverMatchedCount = playAtServerSpeakers(session, res);
            int matchedCount = serverMatchedCount;
            int firstMatchedCount = matchedCount;
            int cachedCount = session.cachedSpeakers != null ? session.cachedSpeakers.size() : 0;
            boolean rescanned = false;

            if (serverMatchedCount == 0) {
                matchedCount = playAtMatchedSpeakers(session, res);
                firstMatchedCount = matchedCount;

                // 専用サーバーではTE同期が遅れることがあるため、未一致時は一度だけ再スキャンする
                if (matchedCount == 0) {
                    initializeSpeakerCache(session, world);
                    matchedCount = playAtMatchedSpeakers(session, res);
                    rescanned = true;
                    cachedCount = session.cachedSpeakers != null ? session.cachedSpeakers.size() : 0;
                }
            }

            if (!session.speakerCheckLogged) {
                session.speakerCheckLogged = true;
                String detail = "serverProvided=" + session.serverSpeakers.size()
                    + ", serverTotal=" + session.serverTotalSpeakers
                    + ", serverKeys=" + (session.serverSampleKeys.isEmpty() ? "none" : session.serverSampleKeys)
                    + ", serverMatched=" + serverMatchedCount
                    + ", cached=" + cachedCount
                    + ", firstMatched=" + firstMatchedCount
                    + ", rescanned=" + rescanned
                    + ", finalMatched=" + matchedCount;
                sendDebugEvent("SPEAKER_CHECK", session.linkKey, soundId, matchedCount, session.playLocalSound, detail);
            }

            // マッチしたスピーカーがある場合のみデバッグパケット送信
            if (matchedCount > 0) {
                sendDebugEvent("PLAY", session.linkKey, soundId, matchedCount, session.playLocalSound, "");
            }

            if (session.playLocalSound) {
                playLocalSound(res, session);
            }

            if (matchedCount == 0 && !session.playLocalSound && !session.noMatchLogged) {
                session.noMatchLogged = true;
                sendDebugEvent("NO_MATCH", session.linkKey, soundId, 0, session.playLocalSound,
                    "serverProvided=" + session.serverSpeakers.size()
                        + ", serverTotal=" + session.serverTotalSpeakers
                        + ", serverKeys=" + (session.serverSampleKeys.isEmpty() ? "none" : session.serverSampleKeys)
                        + ", serverMatched=" + serverMatchedCount
                        + ", cached=" + cachedCount
                        + ", speakerKeys=" + sampleSpeakerKeys(session.cachedSpeakers, 5));
            }

        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Session Playback Error: " + soundId, e);
        }
    }

    private String sampleSpeakerKeys(List<TileEntitySpeaker> speakers, int limit) {
        if (speakers == null || speakers.isEmpty()) {
            return "none";
        }

        StringBuilder sb = new StringBuilder();
        int appended = 0;
        for (TileEntitySpeaker speaker : speakers) {
            if (speaker == null) {
                continue;
            }
            String key = normalizeKey(speaker.linkKey);
            if (key.isEmpty()) {
                continue;
            }
            if (appended > 0) {
                sb.append(",");
            }
            sb.append(key);
            appended++;
            if (appended >= limit) {
                break;
            }
        }

        return appended == 0 ? "none" : sb.toString();
    }

    private void sendDebugEvent(String eventType, String linkKey, String soundId, int matchedSpeakers, boolean playLocalSound, String detail) {
        try {
            NetworkHandler.INSTANCE.sendToServer(new jp.me1han.sam.network.PacketDebugAnnounceEvent(
                eventType,
                linkKey,
                soundId,
                matchedSpeakers,
                playLocalSound,
                detail
            ));
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Failed to send debug event: " + eventType, e);
        }
    }

    private int playAtMatchedSpeakers(AnnounceSession session, ResourceLocation res) {
        int matchedCount = 0;
        if (session.cachedSpeakers == null) {
            return 0;
        }

        for (TileEntitySpeaker speaker : session.cachedSpeakers) {
            if (speaker == null || speaker.linkKey == null) {
                continue;
            }

            String speakerKey = normalizeKey(speaker.linkKey);
            if (!speakerKey.isEmpty() && session.linkKey.equals(speakerKey)) {
                matchedCount++;
                playSoundAtSpeaker(res, session, speaker);
            }
        }

        return matchedCount;
    }

    private int playAtServerSpeakers(AnnounceSession session, ResourceLocation res) {
        if (session.serverSpeakers == null || session.serverSpeakers.isEmpty()) {
            return 0;
        }

        int matchedCount = 0;
        for (PacketAnnounce.SpeakerData speaker : session.serverSpeakers) {
            if (speaker == null || speaker.range < 1 || speaker.volume <= 0) {
                continue;
            }

            try {
                float vol = (speaker.range / 16.0F) * speaker.volume;
                vol = Math.max(0.0F, Math.min(2.0F, vol));

                PositionedSoundRecord psr = new PositionedSoundRecord(res, vol, 1.0F,
                    (float) speaker.x + 0.5F, (float) speaker.y + 0.5F, (float) speaker.z + 0.5F);
                Minecraft.getMinecraft().getSoundHandler().playSound(psr);
                session.activeSounds.add(psr);
                matchedCount++;
            } catch (Exception e) {
                StationAnnounceModCore.logger.error("[SAM] Failed to play sound at server speaker", e);
            }
        }

        return matchedCount;
    }

    private void initializeSpeakerCache(AnnounceSession session, World world) {
        session.cachedSpeakers = new ArrayList<>();

        for (Object obj : world.loadedTileEntityList) {
            if (!(obj instanceof TileEntitySpeaker)) {
                continue;
            }

            TileEntitySpeaker speaker = (TileEntitySpeaker) obj;

            // キャッシュには全Speakerを保持し、マッチング処理は再生時に実施
            session.cachedSpeakers.add(speaker);
        }
    }

    private void playSoundAtSpeaker(ResourceLocation res, AnnounceSession session, TileEntitySpeaker speaker) {
        try {
            if (speaker.range < 1 || speaker.volume <= 0) {
                return;
            }

            float vol = (speaker.range / 16.0F) * speaker.volume;
            vol = Math.max(0.0F, Math.min(2.0F, vol)); // Clamp to reasonable range

            PositionedSoundRecord psr = new PositionedSoundRecord(res, vol, 1.0F,
                (float) speaker.xCoord + 0.5F, (float) speaker.yCoord + 0.5F, (float) speaker.zCoord + 0.5F);
            Minecraft.getMinecraft().getSoundHandler().playSound(psr);
            session.activeSounds.add(psr);
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Failed to play sound at speaker", e);
        }
    }

    private void playLocalSound(ResourceLocation res, AnnounceSession session) {
        try {
            PositionedSoundRecord psr = new PositionedSoundRecord(res, 1.0F, 1.0F,
                (float) session.x + 0.5F, (float) session.y + 0.5F, (float) session.z + 0.5F);
            Minecraft.getMinecraft().getSoundHandler().playSound(psr);
            session.activeSounds.add(psr);
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Failed to play local sound", e);
        }
    }

    private String getFirstSound(PacketAnnounce msg) {
        if (msg.startMelo != null && !msg.startMelo.isEmpty()) {
            return msg.startMelo;
        }
        if (msg.bodySounds != null && !msg.bodySounds.isEmpty()) {
            return msg.bodySounds.get(0);
        }
        return "";
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


