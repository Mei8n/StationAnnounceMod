package jp.me1han.sam.network;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import jp.me1han.sam.StationAnnounceModCore;

/** Forge 1.7.10 logical-server dispatch; bounded to prevent a packet flood starving a tick. */
public final class ServerTaskQueue {
    public static final ServerTaskQueue INSTANCE = new ServerTaskQueue();
    public static final int MAX_PENDING = 1024, MAX_PER_TICK = 256;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    public void enqueue(Runnable task) {
        if (size.incrementAndGet() > MAX_PENDING) { size.decrementAndGet(); return; }
        tasks.add(task);
    }
    @SubscribeEvent public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (int i = 0; i < MAX_PER_TICK; i++) {
            Runnable task = tasks.poll();
            if (task == null) break;
            size.decrementAndGet();
            try { task.run(); } catch (RuntimeException e) { StationAnnounceModCore.logger.error("[SAM] Server task failed", e); }
        }
    }
    public void clear() { Runnable task; while ((task = tasks.poll()) != null) size.decrementAndGet(); }
}
