package jp.me1han.sam.network;

import java.util.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import jp.me1han.sam.SpeakerRegistry;
import jp.me1han.sam.LoadedSamTiles;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

/** Logical server only. No Speaker polling; session expiration is checked every ten seconds. */
public final class ServerSessions {
    public static final ServerSessions INSTANCE = new ServerSessions();
    public static final double LOCAL_RANGE = 16, RANGE_MARGIN = 2;
    public static final long SESSION_TTL_TICKS = 24L * 60 * 60 * 20;
    public static final int CLEANUP_INTERVAL_TICKS = 200;
    private static long serverTick;
    interface Delivery {
        void send(IMessage packet, EntityPlayerMP player);
        void around(IMessage packet, TargetPoint point);
        void all(IMessage packet);
    }
    static Delivery delivery = new Delivery() {
        public void send(IMessage packet, EntityPlayerMP player) { NetworkHandler.INSTANCE.sendTo(packet, player); }
        public void around(IMessage packet, TargetPoint point) { NetworkHandler.INSTANCE.sendToAllAround(packet, point); }
        public void all(IMessage packet) { NetworkHandler.INSTANCE.sendToAll(packet); }
    };
    private static long nextId;
    private static final Map<Long, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Set<Long>> BY_PLAYER = new HashMap<>();
    private static final class Session {
        final long id;
        final TileEntityAnnouncer owner;
        final World world;
        final String key;
        final int priority;
        long routeRevision;
        long expireTick;
        final Set<EntityPlayerMP> recipients = new HashSet<>();
        Session(long id, TileEntityAnnouncer owner, PacketAnnounce packet) {
            this.id = id; this.owner = owner; world = owner.getWorldObj();
            key = SpeakerRegistry.normalize(packet.linkKey); priority = packet.priority;
            expireTick = serverTick + SESSION_TTL_TICKS;
        }
    }
    private ServerSessions() {}
    public static boolean within(double px, double py, double pz, double x, double y, double z, double range) {
        double dx = px-x, dy = py-y, dz = pz-z;
        return dx*dx + dy*dy + dz*dz < range*range;
    }
    public static long start(TileEntityAnnouncer owner, PacketAnnounce packet) {
        World world = owner.getWorldObj();
        if (world == null || world.isRemote) return 0;
        if (!(packet instanceof PacketDepartureStart)) {
            try {
                // Resolve once from the logical server's pack, before recipient-specific copies.
                packet.resolveTiming(jp.me1han.sam.AnnouncePackLoader.soundTicks);
            } catch (IllegalArgumentException invalid) {
                jp.me1han.sam.StationAnnounceModCore.logger.warn(
                    "[SAM] Announcement rejected: " + invalid.getMessage());
                return 0;
            }
        }
        packet.linkKey = SpeakerRegistry.normalize(packet.linkKey);
        packet.sessionId = ++nextId;
        Session session = new Session(packet.sessionId, owner, packet);
        // START describes a logical timeline. Routing is resolved from synchronized
        // client Speaker state at each playback boundary, so every current player in
        // this World must learn about the session even when it is presently inaudible.
        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP)obj;
            if (player.worldObj != world) continue;
            session.recipients.add(player);
            BY_PLAYER.computeIfAbsent(player.getUniqueID(), id -> new HashSet<>()).add(session.id);
        }
        if (session.recipients.isEmpty()) return session.id;
        SESSIONS.put(session.id, session);
        // One logical START followed by one complete, chunked routing revision.
        List<PacketSessionSpeakerRoutes> routes = routeSnapshot(session);
        for (EntityPlayerMP player : session.recipients) {
            delivery.send(copy(packet, new long[0]), player);
            for (PacketSessionSpeakerRoutes route : routes) delivery.send(route, player);
        }
        return session.id;
    }

    private static List<PacketSessionSpeakerRoutes> routeSnapshot(Session session) {
        List<PacketSessionSpeakerRoutes.Target> targets = new ArrayList<>();
        for (SpeakerRegistry.Entry speaker : SpeakerRegistry.findByKey(session.world, session.key)) {
            if (speaker.tile.isInvalid() || !PacketLimits.speaker(speaker.range, speaker.volume)) continue;
            targets.add(new PacketSessionSpeakerRoutes.Target(
                SpeakerRegistry.position(speaker.x, speaker.y, speaker.z), speaker.range, speaker.volume));
        }
        int chunkCount = (int)Math.max(1L, (targets.size() + (long)PacketLimits.SESSION_TARGETS - 1)
            / PacketLimits.SESSION_TARGETS);
        long revision = ++session.routeRevision;
        List<PacketSessionSpeakerRoutes> packets = new ArrayList<>(chunkCount);
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            PacketSessionSpeakerRoutes packet = new PacketSessionSpeakerRoutes(
                session.id, revision, chunkIndex, chunkCount);
            int from = chunkIndex * PacketLimits.SESSION_TARGETS;
            int to = Math.min(targets.size(), from + PacketLimits.SESSION_TARGETS);
            packet.targets.addAll(targets.subList(from, to));
            packets.add(packet);
        }
        return packets;
    }

    /** Called only by event-driven server SpeakerRegistry mutations. */
    public static void speakersChanged(World world, String oldKey, String newKey) {
        if (world == null || world.isRemote) return;
        String oldNormalized = oldKey == null ? null : SpeakerRegistry.normalize(oldKey);
        String newNormalized = newKey == null ? null : SpeakerRegistry.normalize(newKey);
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            if (session.world != world) continue;
            if (!session.key.equals(oldNormalized) && !session.key.equals(newNormalized)) continue;
            for (PacketSessionSpeakerRoutes packet : routeSnapshot(session)) send(session, packet);
        }
    }
    private static PacketAnnounce copy(PacketAnnounce source, long[] targets) {
        PacketAnnounce result;
        if (source instanceof PacketDepartureStart) {
            PacketDepartureStart departure = new PacketDepartureStart();
            departure.departure = ((PacketDepartureStart)source).departure;
            result = departure;
        } else result = new PacketAnnounce();
        result.sessionId = source.sessionId; result.linkKey = source.linkKey;
        result.priority = source.priority; result.allowOverlap = source.allowOverlap;
        result.playLocalSound = source.playLocalSound;
        result.x = source.x; result.y = source.y; result.z = source.z; result.targets = targets;
        result.startMelo = source.startMelo; result.arrMelo = source.arrMelo; result.bodySounds = source.bodySounds;
        result.bodyIntervalTicks = source.bodyIntervalTicks;
        result.startMeloTicks = source.startMeloTicks; result.arrMeloTicks = source.arrMeloTicks;
        result.bodyPartTicks = source.bodyPartTicks;
        result.repeatCount = source.repeatCount;
        return result;
    }
    private static void send(Session session, IMessage message) {
        for (EntityPlayerMP player : session.recipients)
            if (player.worldObj == session.world && player.playerNetServerHandler != null
                && player.playerNetServerHandler.netManager.isChannelOpen())
                delivery.send(message, player);
    }
    public static void control(long id, boolean cancel) {
        Session session = SESSIONS.get(id);
        if (session == null || session.priority != PacketAnnounce.PRIORITY_DEPARTURE_MELODY) return;
        send(session, new PacketDepartureControl(id, cancel));
        if (cancel) remove(session);
        else session.expireTick = serverTick + SESSION_TTL_TICKS;
    }
    private static void stop(Session session) {
        send(session, new PacketAnnounceStop(session.id)); remove(session);
    }
    public static void stopKey(World world, String key) {
        String normalized = SpeakerRegistry.normalize(key);
        for (Session session : new ArrayList<>(SESSIONS.values()))
            if (session.world == world && session.key.equals(normalized)) stop(session);
    }
    public static void stopOwner(TileEntityAnnouncer owner) {
        if (owner.getWorldObj() == null || owner.getWorldObj().isRemote) return;
        for (Session session : new ArrayList<>(SESSIONS.values())) if (session.owner == owner) stop(session);
    }
    public static void stopPriority(TileEntityAnnouncer owner, int priority) {
        if (owner == null || owner.getWorldObj() == null || owner.getWorldObj().isRemote) return;
        for (Session session : new ArrayList<>(SESSIONS.values()))
            if (session.owner == owner && session.priority == priority) stop(session);
    }
    public static void stopAll() {
        delivery.all(new PacketAnnounceStop(0));
        clear();
    }
    public static void finished(EntityPlayerMP player, long id) {
        Session session = SESSIONS.get(id);
        if (session == null || !session.recipients.remove(player)) return;
        forgetPlayer(player.getUniqueID(), id);
        if (session.recipients.isEmpty()) SESSIONS.remove(id);
    }
    private static void forgetPlayer(UUID player, long id) {
        Set<Long> ids = BY_PLAYER.get(player);
        if (ids != null) { ids.remove(id); if (ids.isEmpty()) BY_PLAYER.remove(player); }
    }
    private static void remove(Session session) {
        SESSIONS.remove(session.id);
        for (EntityPlayerMP player : session.recipients) forgetPlayer(player.getUniqueID(), session.id);
        session.recipients.clear();
    }
    private static void detach(net.minecraft.entity.player.EntityPlayer player) {
        Set<Long> ids = BY_PLAYER.remove(player.getUniqueID());
        if (ids == null) return;
        for (Long id : ids) {
            Session session = SESSIONS.get(id);
            if (session == null) continue;
            session.recipients.removeIf(p -> p.getUniqueID().equals(player.getUniqueID()));
            if (session.recipients.isEmpty()) SESSIONS.remove(id);
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { detach(event.player); }
    @SubscribeEvent public void changedWorld(PlayerEvent.PlayerChangedDimensionEvent event) { detach(event.player); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        // Same-dimension respawn can retain WorldClient. Stop its old sessions explicitly.
        Set<Long> ids = BY_PLAYER.get(event.player.getUniqueID());
        if (ids != null && event.player instanceof EntityPlayerMP)
            for (Long id : ids) delivery.send(new PacketAnnounceStop(id), (EntityPlayerMP) event.player);
        detach(event.player);
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        for (Session session : new ArrayList<>(SESSIONS.values())) if (session.world == event.world) stop(session);
        SpeakerRegistry.clear(event.world); jp.me1han.sam.link.SamLinkRegistry.clear(event.world);
        LoadedSamTiles.clear(event.world);
    }
    public static void clear() { SESSIONS.clear(); BY_PLAYER.clear(); serverTick = 0; }

    @SubscribeEvent public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++serverTick % CLEANUP_INTERVAL_TICKS == 0) expireSessions(serverTick);
    }

    // Package access also permits testing expiry without simulating a day of game ticks.
    static void expireSessions(long now) {
        for (Session session : new ArrayList<>(SESSIONS.values()))
            if (now >= session.expireTick) stop(session);
    }

    public static void missing(EntityPlayerMP player, PacketMissingSpeakers request) {
        Session session = SESSIONS.get(request.sessionId);
        if (session == null || player.worldObj != session.world || !session.recipients.contains(player)
            || player.playerNetServerHandler == null || !player.playerNetServerHandler.netManager.isChannelOpen()
            || request.targets == null || request.targets.length > PacketLimits.MISSING_TARGETS) return;
        // Dynamic START packets contain no server-selected coordinates. Keeping this
        // legacy endpoint non-authoritative prevents arbitrary coordinate lookups.
    }
}
