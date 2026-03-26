package jp.me1han.sam.client;

import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.StationAnnounceModCore;
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
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AnnounceManager {
    public static final AnnounceManager INSTANCE = new AnnounceManager();

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private String loopSound = null;
    private String currentLinkKey = null; // 現在の放送に紐付いたリンクキー
    private int waitTicks = 0;
    private volatile boolean isPlaying = false;

    private final List<ISound> activeSounds = new ArrayList<ISound>();

    public void startAnnounce(PacketAnnounce msg) {
        this.stopAnnounce();
        this.currentLinkKey = msg.linkKey;

        if (msg.startMelo != null && !msg.startMelo.isEmpty()) {
            this.queue.add(msg.startMelo);
        }
        if (msg.bodySounds != null) {
            for (String s : msg.bodySounds) {
                if (s != null && !s.isEmpty()) this.queue.add(s);
            }
        }

        this.loopSound = (msg.arrMelo != null && !msg.arrMelo.isEmpty()) ? msg.arrMelo : null;
        this.waitTicks = 0;
        this.isPlaying = true;
    }

    public void stopAnnounce() {
        this.isPlaying = false;

        for (ISound s : activeSounds) {
            if (s != null) {
                Minecraft.getMinecraft().getSoundHandler().stopSound(s);
            }
        }
        activeSounds.clear();

        this.queue.clear();
        this.loopSound = null;
        this.currentLinkKey = null;
        this.waitTicks = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isPlaying) return;

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        String nextSound = queue.poll();

        if (nextSound != null) {
            this.playAndRegister(nextSound);
            setWaitTicks(nextSound);
        }
        else if (loopSound != null) {
            this.playAndRegister(loopSound);
            setWaitTicks(loopSound);
        }
        else {
            this.isPlaying = false;
        }
    }

    private void playAndRegister(String soundId) {
        activeSounds.clear();

        List<ISound> playedSounds = playSound(soundId);
        if (playedSounds != null) {
            activeSounds.addAll(playedSounds);
        }
    }

    private void setWaitTicks(String soundId) {
        if (soundId == null) {
            this.waitTicks = 20;
            return;
        }
        Integer ticks = AnnouncePackLoader.soundTicks.get(soundId);
        this.waitTicks = (ticks != null) ? ticks : 20;
    }

    private List<ISound> playSound(String soundId) {
        List<ISound> sounds = new ArrayList<ISound>();
        if (soundId == null || soundId.isEmpty() || !isPlaying) return sounds;

        try {
            ResourceLocation res = new ResourceLocation(soundId);
            World world = Minecraft.getMinecraft().theWorld;
            boolean speakerFound = false;

            for (Object obj : world.loadedTileEntityList) {
                if (obj instanceof TileEntitySpeaker) {
                    TileEntitySpeaker speaker = (TileEntitySpeaker) obj;

                    if (speaker.linkKey != null && speaker.linkKey.equals(this.currentLinkKey)) {

                         float effectiveVolume = (speaker.range / 16.0F) * speaker.volume;

                        PositionedSoundRecord psr = new PositionedSoundRecord(
                            res,
                            effectiveVolume, 1.0F,
                            (float)speaker.xCoord + 0.5F,
                            (float)speaker.yCoord + 0.5F,
                            (float)speaker.zCoord + 0.5F
                        );
                        Minecraft.getMinecraft().getSoundHandler().playSound(psr);
                        sounds.add(psr);
                        speakerFound = true;
                    }
                }
            }

            if (!speakerFound) {
                PositionedSoundRecord psr = PositionedSoundRecord.func_147674_a(res, 1.0F);
                Minecraft.getMinecraft().getSoundHandler().playSound(psr);
                sounds.add(psr);
            }

        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Sound Playback Error: " + soundId, e);
        }

        return sounds;
    }
}
