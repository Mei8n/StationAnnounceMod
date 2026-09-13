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
    public enum Priority { CRITICAL, NORMAL }

    public static final int MAX_PENDING = 1024;
    public static final int MAX_PENDING_PER_SENDER = 64;
    public static final int MAX_CRITICAL_PENDING_GLOBAL = 128;
    public static final int MAX_CRITICAL_PENDING_PER_SENDER = 8;
    public static final int MAX_PER_TICK = 256;
    public static final int MAX_PER_SENDER_PER_TICK = 32;
    public static final int MAX_CRITICAL_BURST_PER_SENDER = 4;

    private final Object lock = new Object();
    private final Map<UUID, SenderQueue> senders = new HashMap<>();
    private final ArrayDeque<SenderQueue> activeSenders = new ArrayDeque<>();
    private int pendingCount;
    private int normalPendingCount;
    private int criticalPendingCount;

    private static final class SenderQueue {
        final UUID sender;
        final ArrayDeque<Runnable> normal = new ArrayDeque<>();
        final ArrayDeque<Runnable> critical = new ArrayDeque<>();
        int criticalBurst;
        boolean scheduled;
        SenderQueue(UUID sender) { this.sender = sender; }
        boolean isEmpty() { return normal.isEmpty() && critical.isEmpty(); }
        int size() { return normal.size() + critical.size(); }
    }

    /** NORMAL is the concise default for existing GUI/config handlers. */
    public boolean enqueue(EntityPlayerMP player, Runnable task) {
        return enqueue(player, Priority.NORMAL, task);
    }

    /** Sender identity comes from the server-side MessageContext, never packet data. */
    public boolean enqueue(EntityPlayerMP player, Priority priority, Runnable task) {
        if (player == null || task == null) return false;
        if (priority == null) priority = Priority.NORMAL;
        UUID sender = player.getUniqueID();
        if (sender == null) return false;
        synchronized (lock) {
            SenderQueue queue = senders.get(sender);
            if (priority == Priority.CRITICAL) {
                if (queue != null && queue.critical.size() >= MAX_CRITICAL_PENDING_PER_SENDER) return false;
                if (criticalPendingCount >= MAX_CRITICAL_PENDING_GLOBAL) return false;
            } else {
                if (queue != null && queue.normal.size() >= MAX_PENDING_PER_SENDER) return false;
                if (normalPendingCount >= MAX_PENDING) return false;
            }
            if (queue == null) {
                queue = new SenderQueue(sender);
                senders.put(sender, queue);
            }
            if (priority == Priority.CRITICAL) {
                queue.critical.addLast(task);
                criticalPendingCount++;
            } else {
                queue.normal.addLast(task);
                normalPendingCount++;
            }
            pendingCount++;
            schedule(queue);
            return true;
        }
    }

    private void schedule(SenderQueue queue) {
        if (queue.scheduled || queue.isEmpty()) return;
        queue.scheduled = true;
        activeSenders.addLast(queue);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        synchronized (lock) { if (activeSenders.isEmpty()) return; }
        Map<UUID, Integer> processed = new HashMap<>();
        List<SenderQueue> deferred = new ArrayList<>();
        try {
            for (int i = 0; i < MAX_PER_TICK; i++) {
                Runnable task;
                synchronized (lock) {
                    SenderQueue queue = activeSenders.pollFirst();
                    if (queue == null) break;
                    queue.scheduled = false;
                    boolean takeCritical = !queue.critical.isEmpty()
                        && (queue.normal.isEmpty() || queue.criticalBurst < MAX_CRITICAL_BURST_PER_SENDER);
                    if (takeCritical) {
                        task = queue.critical.pollFirst();
                        queue.criticalBurst++;
                        criticalPendingCount--;
                    } else {
                        task = queue.normal.pollFirst();
                        queue.criticalBurst = 0;
                        normalPendingCount--;
                    }
                    if (task == null) {
                        senders.remove(queue.sender, queue);
                        i--;
                        continue;
                    }
                    pendingCount--;
                    int senderProcessed = processed.containsKey(queue.sender)
                        ? processed.get(queue.sender) + 1 : 1;
                    processed.put(queue.sender, senderProcessed);
                    if (queue.isEmpty()) senders.remove(queue.sender, queue);
                    else if (senderProcessed >= MAX_PER_SENDER_PER_TICK) {
                        queue.scheduled = true;
                        deferred.add(queue);
                    } else schedule(queue);
                }
                try { task.run(); }
                catch (RuntimeException e) { StationAnnounceModCore.logger.error("[SAM] Server task failed", e); }
            }
        } finally {
            synchronized (lock) {
                for (SenderQueue queue : deferred) {
                    if (senders.get(queue.sender) != queue || queue.isEmpty()) continue;
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
            pendingCount -= queue.size();
            normalPendingCount -= queue.normal.size();
            criticalPendingCount -= queue.critical.size();
            queue.normal.clear();
            queue.critical.clear();
            queue.scheduled = false;
        }
    }

    public void clear() {
        synchronized (lock) {
            senders.clear();
            activeSenders.clear();
            pendingCount = normalPendingCount = criticalPendingCount = 0;
        }
    }

    int pendingCount() { synchronized (lock) { return pendingCount; } }
    int pendingCount(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null) return 0;
        synchronized (lock) {
            SenderQueue queue = senders.get(player.getUniqueID());
            return queue == null ? 0 : queue.size();
        }
    }
    int normalPendingCount(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null) return 0;
        synchronized (lock) {
            SenderQueue queue = senders.get(player.getUniqueID());
            return queue == null ? 0 : queue.normal.size();
        }
    }
    int criticalPendingCount(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null) return 0;
        synchronized (lock) {
            SenderQueue queue = senders.get(player.getUniqueID());
            return queue == null ? 0 : queue.critical.size();
        }
    }
    int activeSenderCount() { synchronized (lock) { return senders.size(); } }
}
