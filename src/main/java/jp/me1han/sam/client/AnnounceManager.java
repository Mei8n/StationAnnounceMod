package jp.me1han.sam.client;

import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.network.MessageAnnounce;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AnnounceManager {
    public static final AnnounceManager INSTANCE = new AnnounceManager();

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private String loopSound = null;
    private int waitTicks = 0;
    private volatile boolean isPlaying = false;

    // 現在再生中のサウンドを保持する変数
    private ISound currentSound = null;

    public void startAnnounce(MessageAnnounce msg) {
        this.stopAnnounce();

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

    /**
     * 即時停止処理
     */
    public void stopAnnounce() {
        this.isPlaying = false;

        // 1. 現在再生中の音を強制停止
        if (this.currentSound != null) {
            Minecraft.getMinecraft().getSoundHandler().stopSound(this.currentSound);
            this.currentSound = null;
        }

        // 2. 予約されているパーツをすべて破棄
        this.queue.clear();
        this.loopSound = null;
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
            this.currentSound = playSound(nextSound);
            setWaitTicks(nextSound);
        }
        else if (loopSound != null) {
            this.currentSound = playSound(loopSound);
            setWaitTicks(loopSound);
        }
        else {
            this.isPlaying = false;
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

    /**
     * サウンドを再生し、そのインスタンスを返す
     */
    private ISound playSound(String soundId) {
        if (soundId == null || soundId.isEmpty() || !isPlaying) return null;

        try {
            ResourceLocation res = new ResourceLocation(soundId);
            PositionedSoundRecord psr = PositionedSoundRecord.func_147674_a(res, 1.0F);
            Minecraft.getMinecraft().getSoundHandler().playSound(psr);
            return psr; // 停止操作のためにインスタンスを返す
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Sound Playback Error: " + soundId);
            return null;
        }
    }
}
