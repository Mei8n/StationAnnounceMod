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

    private final Map<String, AnnounceSession> activeSessions = new ConcurrentHashMap<String, AnnounceSession>();

    private class AnnounceSession {
        final String linkKey;
        final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<String>();
        String loopSound = null;
        boolean playLocalSound = false;
        int x, y, z;
        int waitTicks = 0;
        boolean isPlaying = true;
        final List<ISound> activeSounds = new ArrayList<ISound>();

        AnnounceSession(PacketAnnounce msg) {
            this.linkKey = msg.linkKey != null ? msg.linkKey : "GLOBAL_EMPTY";
            this.playLocalSound = msg.playLocalSound;
            this.x = msg.x; this.y = msg.y; this.z = msg.z;

            if (msg.startMelo != null && !msg.startMelo.isEmpty()) this.queue.add(msg.startMelo);
            if (msg.bodySounds != null) {
                for (String s : msg.bodySounds) {
                    if (s != null && !s.isEmpty()) this.queue.add(s);
                }
            }
            this.loopSound = (msg.arrMelo != null && !msg.arrMelo.isEmpty()) ? msg.arrMelo : null;
        }

        void stop() {
            this.isPlaying = false;
            for (ISound s : activeSounds) {
                if (s != null) Minecraft.getMinecraft().getSoundHandler().stopSound(s);
            }
            activeSounds.clear();
            queue.clear();
        }
    }

    public void startAnnounce(PacketAnnounce msg) {
        String key = normalizeKey(msg.linkKey);

        StationAnnounceModCore.logger.info("[SAM-DEBUG] Client startAnnounce received. key=[" + key + "], playLocal=" + msg.playLocalSound + ", pos=" + msg.x + "," + msg.y + "," + msg.z);

        if (activeSessions.containsKey(key)) {
            activeSessions.get(key).stop();
        }

        activeSessions.put(key, new AnnounceSession(msg));

        // First sound for debug output
        String firstSound = (msg.startMelo != null && !msg.startMelo.isEmpty()) ? msg.startMelo :
                            (msg.bodySounds != null && !msg.bodySounds.isEmpty()) ? msg.bodySounds.get(0) : "";

        // Send debug event (will be processed server-side)
        NetworkHandler.INSTANCE.sendToServer(new jp.me1han.sam.network.PacketDebugAnnounceEvent("START", key, firstSound, 0, msg.playLocalSound));
    }

    public void stopAnnounce(String linkKey) {
        String normalizedKey = normalizeKey(linkKey);

        if (PacketAnnounce.GLOBAL_STOP_KEY.equals(normalizedKey) || normalizedKey.isEmpty()) {
            for (AnnounceSession session : activeSessions.values()) {
                NetworkHandler.INSTANCE.sendToServer(new jp.me1han.sam.network.PacketDebugAnnounceEvent("STOP", session.linkKey, "", 0, session.playLocalSound));
                session.stop();
            }
            activeSessions.clear();
        }
        else if (activeSessions.containsKey(normalizedKey)) {
            AnnounceSession session = activeSessions.get(normalizedKey);
            NetworkHandler.INSTANCE.sendToServer(new jp.me1han.sam.network.PacketDebugAnnounceEvent("STOP", normalizedKey, "", 0, session.playLocalSound));
            session.stop();
            activeSessions.remove(normalizedKey);
        }
    }

    public void stopAnnounce() {
        this.stopAnnounce(null);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || activeSessions.isEmpty()) return;

        Iterator<Map.Entry<String, AnnounceSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            AnnounceSession session = it.next().getValue();

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
        Integer ticks = AnnouncePackLoader.soundTicks.get(soundId);
        return (ticks != null) ? ticks : 20;
    }

    private void playInSession(AnnounceSession session, String soundId) {
        session.activeSounds.clear();
        try {
            ResourceLocation res = new ResourceLocation(soundId);
            World world = Minecraft.getMinecraft().theWorld;
            if (world == null) {
                StationAnnounceModCore.logger.warn("[SAM-DEBUG] playInSession skipped: client world is null");
                return;
            }

            int speakerCount = 0;
            int matchedCount = 0;
            String sessionKey = normalizeKey(session.linkKey);

            for (Object obj : world.loadedTileEntityList) {
                if (obj instanceof TileEntitySpeaker) {
                    speakerCount++;
                    TileEntitySpeaker speaker = (TileEntitySpeaker) obj;
                    String speakerKey = normalizeKey(speaker.linkKey);
                    if (!speakerKey.isEmpty() && sessionKey.equals(speakerKey)) {
                        matchedCount++;

                        float vol = (speaker.range / 16.0F) * speaker.volume;
                        PositionedSoundRecord psr = new PositionedSoundRecord(res, vol, 1.0F,
                            (float)speaker.xCoord + 0.5F, (float)speaker.yCoord + 0.5F, (float)speaker.zCoord + 0.5F);
                        Minecraft.getMinecraft().getSoundHandler().playSound(psr);
                        session.activeSounds.add(psr);
                    }
                }
            }

            StationAnnounceModCore.logger.info("[SAM-DEBUG] Speaker scan done. sessionKey=[" + sessionKey + "], loadedSpeakers=" + speakerCount + ", matched=" + matchedCount + ", sound=" + soundId);

            // Update debug event packet with matched speaker count
            if (!session.linkKey.isEmpty() && matchedCount > 0) {
                NetworkHandler.INSTANCE.sendToServer(new jp.me1han.sam.network.PacketDebugAnnounceEvent("PLAY", sessionKey, soundId, matchedCount, session.playLocalSound));
            }

            if (session.playLocalSound) {
                PositionedSoundRecord psr = new PositionedSoundRecord(res, 1.0F, 1.0F,
                    (float)session.x + 0.5F, (float)session.y + 0.5F, (float)session.z + 0.5F);
                Minecraft.getMinecraft().getSoundHandler().playSound(psr);
                session.activeSounds.add(psr);
            }

            if (matchedCount == 0 && !session.playLocalSound) {
                StationAnnounceModCore.logger.warn("[SAM-DEBUG] No speaker matched key=[" + sessionKey + "] on this client.");
            }

        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Session Playback Error: " + soundId, e);
        }
    }

    private String normalizeKey(String key) {
        if (key == null) return "";
        return key.trim();
    }
}
