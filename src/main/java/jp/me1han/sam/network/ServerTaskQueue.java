package jp.me1han.sam.network;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jp.me1han.sam.StationAnnounceModCore;
import net.minecraft.entity.player.EntityPlayerMP;

/** Forge 1.7.10 logical-server dispatch with bounded per-sender fair scheduling. */
public final class ServerTaskQueue {
    public static final ServerTaskQueue INSTANCE = new ServerTaskQueue();

    /** 64 tolerates short GUI bursts; 32 leaves the 256-task tick budget shared by up to eight busy senders. */
    public static final int MAX_PENDING = 1024;
    public static final int MAX_PENDING_PER_SENDER = 64;
    public static final int MAX_PER_TICK = 256;
    public static final int MAX_PER_SENDER_PER_TICK = 32;

    private final Object lock = new Object();
    private final Map<UUID, SenderQueue> senders = new HashMap<>();
    private final ArrayDeque<SenderQueue> activeSenders = new ArrayDeque<>();
    private int pendingCount;

    private static final class SenderQueue {
        final UUID sender;
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        boolean scheduled;

        SenderQueue(UUID sender) { this.sender = sender; }
    }

    /** Sender identity is obtained from the server-side MessageContext, never packet data. */
    public boolean enqueue(EntityPlayerMP player, Runnable task) {
        if (player == null || task == null) return false;
        UUID sender = player.getUniqueID();
        if (sender == null) return false;
        synchronized (lock) {
            SenderQueue queue = senders.get(sender);
            if (queue != null && queue.tasks.size() >= MAX_PENDING_PER_SENDER) return false;
            if (pendingCount >= MAX_PENDING) return false;
            if (queue == null) {
                queue = new SenderQueue(sender);
                senders.put(sender, queue);
            }
            queue.tasks.addLast(task);
            pendingCount++;
            schedule(queue);
            return true;
        }
    }

    private void schedule(SenderQueue queue) {
        if (queue.scheduled || queue.tasks.isEmpty()) return;
        queue.scheduled = true;
        activeSenders.addLast(queue);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        synchronized (lock) {
            if (activeSenders.isEmpty()) return;
        }
        Map<UUID, Integer> processed = new HashMap<>();
        List<SenderQueue> deferred = new ArrayList<>();
        try {
            for (int i = 0; i < MAX_PER_TICK; i++) {
                Runnable task;
                synchronized (lock) {
                    SenderQueue queue = activeSenders.pollFirst();
                    if (queue == null) break;
                    queue.scheduled = false;
                    task = queue.tasks.pollFirst();
                    if (task == null) {
                        senders.remove(queue.sender, queue);
                        i--;
                        continue;
                    }
                    pendingCount--;
                    int senderProcessed = processed.containsKey(queue.sender)
                        ? processed.get(queue.sender) + 1 : 1;
                    processed.put(queue.sender, senderProcessed);
                    if (queue.tasks.isEmpty()) senders.remove(queue.sender, queue);
                    else if (senderProcessed >= MAX_PER_SENDER_PER_TICK) {
                        queue.scheduled = true;
                        deferred.add(queue);
                    } else schedule(queue);
                }
                try {
                    task.run();
                } catch (RuntimeException e) {
                    StationAnnounceModCore.logger.error("[SAM] Server task failed", e);
                }
            }
        } finally {
            synchronized (lock) {
                for (SenderQueue queue : deferred) {
                    if (senders.get(queue.sender) != queue || queue.tasks.isEmpty()) continue;
                    queue.scheduled = false;
                    schedule(queue);
                }
            }
        }
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { discard(event.player.getUniqueID()); }
    @SubscribeEvent public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) { discard(event.player.getUniqueID()); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { discard(event.player.getUniqueID()); }

    private void discard(UUID sender) {
        if (sender == null) return;
        synchronized (lock) {
            SenderQueue queue = senders.remove(sender);
            if (queue == null) return;
            activeSenders.remove(queue);
            pendingCount -= queue.tasks.size();
            queue.tasks.clear();
            queue.scheduled = false;
        }
    }

    public void clear() {
        synchronized (lock) {
            senders.clear();
            activeSenders.clear();
            pendingCount = 0;
        }
    }

    int pendingCount() { synchronized (lock) { return pendingCount; } }
    int pendingCount(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null) return 0;
        synchronized (lock) {
            SenderQueue queue = senders.get(player.getUniqueID());
            return queue == null ? 0 : queue.tasks.size();
        }
    }
    int activeSenderCount() { synchronized (lock) { return senders.size(); } }
}
