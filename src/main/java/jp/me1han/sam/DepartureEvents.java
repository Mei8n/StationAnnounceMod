package jp.me1han.sam;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.concurrent.ConcurrentLinkedQueue;

/** GUI configuration writes are applied on the logical server thread. */
public final class DepartureEvents {
    public static final DepartureEvents INSTANCE = new DepartureEvents();
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    public void enqueue(Runnable task) { tasks.add(task); }
    @SubscribeEvent public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Runnable task;
        while ((task = tasks.poll()) != null) task.run();
    }
}
