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
        String loopSound;
        boolean playLocalSound;
        int x, y, z;
        int waitTicks = 0;
        boolean isPlaying = true;
        final List<ISound> activeSounds = new ArrayList<>();

        // Speakerキャッシュ: セッション開始時の1回だけスキャン
        List<TileEntitySpeaker> cachedSpeakers;
        boolean speakersInitialized = false;

        AnnounceSession(PacketAnnounce msg) {
            this.linkKey = normalizeKeyStatic(msg.linkKey);
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
            StationAnnounceModCore.logger.warn("[SAM-DEBUG] startAnnounce called with null message or linkKey");
            return;
        }

        String key = normalizeKey(msg.linkKey);

        StationAnnounceModCore.logger.info("[SAM-DEBUG] Client startAnnounce received. key=[" + key + "], playLocal=" + msg.playLocalSound + ", pos=" + msg.x + "," + msg.y + "," + msg.z);

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
                StationAnnounceModCore.logger.warn("[SAM-DEBUG] playInSession skipped: client world is null");
                return;
            }

            // Speakerキャッシュの初期化（セッション開始時の1回のみ）
            if (!session.speakersInitialized) {
                initializeSpeakerCache(session, world);
                session.speakersInitialized = true;
            }

            int matchedCount = 0;
            if (session.cachedSpeakers != null) {
                for (TileEntitySpeaker speaker : session.cachedSpeakers) {
                    if (speaker != null && speaker.linkKey != null) {
                        String speakerKey = normalizeKey(speaker.linkKey);
                        if (!speakerKey.isEmpty() && session.linkKey.equals(speakerKey)) {
                            matchedCount++;
                            playSoundAtSpeaker(res, session, speaker);
                        }
                    }
                }
            }

            // 初回スキャン時のみログ出力（毎フレームログ出力を廃止）
            if (!session.speakersInitialized) {
                StationAnnounceModCore.logger.info("[SAM-DEBUG] Speaker cache initialized for key=[" + session.linkKey + "], count=" + (session.cachedSpeakers != null ? session.cachedSpeakers.size() : 0));
            }

            // マッチしたスピーカーがある場合のみデバッグパケット送信
            if (matchedCount > 0) {
                try {
                    NetworkHandler.INSTANCE.sendToServer(new jp.me1han.sam.network.PacketDebugAnnounceEvent("PLAY", session.linkKey, soundId, matchedCount, session.playLocalSound));
                } catch (Exception e) {
                    StationAnnounceModCore.logger.error("[SAM] Failed to send debug play event", e);
                }
            }

            if (session.playLocalSound) {
                playLocalSound(res, session);
            }

            if (matchedCount == 0 && !session.playLocalSound) {
                StationAnnounceModCore.logger.warn("[SAM-DEBUG] No speaker matched key=[" + session.linkKey + "] on this client.");
            }

        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Session Playback Error: " + soundId, e);
        }
    }

    private void initializeSpeakerCache(AnnounceSession session, World world) {
        session.cachedSpeakers = new ArrayList<>();
        String sessionKey = normalizeKey(session.linkKey);

        for (Object obj : world.loadedTileEntityList) {
            if (!(obj instanceof TileEntitySpeaker)) {
                continue;
            }

            TileEntitySpeaker speaker = (TileEntitySpeaker) obj;
            String speakerKey = normalizeKey(speaker.linkKey);

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


