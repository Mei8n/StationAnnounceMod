package jp.me1han.sam;

import java.util.*;
import jp.me1han.sam.render.TileEntitySpeaker;
import net.minecraft.world.World;

/** Server-only lifecycle index. World identity isolates integrated-server reloads. */
public final class SpeakerRegistry {
    private static final Map<World, DimensionSpeakers> WORLDS = new IdentityHashMap<>();
    private static final class DimensionSpeakers {
        final Map<Long, Entry> byPosition = new HashMap<>();
        final Map<String, Map<Long, Entry>> byLinkKey = new HashMap<>();
    }
    public static final class Entry {
        public final TileEntitySpeaker tile;
        public final String linkKey;
        public final int x, y, z, range;
        public final float volume;
        Entry(TileEntitySpeaker tile) {
            this.tile = tile; linkKey = normalize(tile.linkKey);
            x = tile.xCoord; y = tile.yCoord; z = tile.zCoord;
            range = tile.range; volume = tile.volume;
        }
    }
    private SpeakerRegistry() {}
    public static String normalize(String key) { return key == null ? "" : key.trim(); }
    /** 26-bit signed X/Z, 12-bit Y; supports the Minecraft world limits and y=0. */
    public static long position(int x, int y, int z) {
        return ((long)x & 0x3ffffffL) << 38 | ((long)z & 0x3ffffffL) << 12 | (y & 0xfffL);
    }
    public static int x(long pos) { return (int)(pos >> 38); }
    public static int y(long pos) { return (int)(pos & 0xfffL); }
    public static int z(long pos) { return (int)(pos << 26 >> 38); }
    public static void register(TileEntitySpeaker tile) {
        World world = tile.getWorldObj();
        if (world == null || world.isRemote || tile.isInvalid()) return;
        DimensionSpeakers registry = WORLDS.computeIfAbsent(world, w -> new DimensionSpeakers());
        long pos = position(tile.xCoord, tile.yCoord, tile.zCoord);
        Entry old = registry.byPosition.get(pos);
        String key = normalize(tile.linkKey);
        if (old != null && old.tile == tile && old.linkKey.equals(key)
            && old.range == tile.range && old.volume == tile.volume) return;
        if (old != null) remove(registry, pos, old);
        Entry entry = new Entry(tile);
        registry.byPosition.put(pos, entry);
        registry.byLinkKey.computeIfAbsent(key, k -> new HashMap<>()).put(pos, entry);
    }
    public static void unregister(TileEntitySpeaker tile) {
        DimensionSpeakers registry = WORLDS.get(tile.getWorldObj());
        if (registry == null) return;
        long pos = position(tile.xCoord, tile.yCoord, tile.zCoord);
        Entry old = registry.byPosition.get(pos);
        // Late invalidation of an old TE must not remove its replacement.
        if (old != null && old.tile == tile) remove(registry, pos, old);
        if (registry.byPosition.isEmpty()) WORLDS.remove(tile.getWorldObj());
    }
    private static void remove(DimensionSpeakers registry, long pos, Entry old) {
        registry.byPosition.remove(pos);
        Map<Long, Entry> group = registry.byLinkKey.get(old.linkKey);
        group.remove(pos);
        if (group.isEmpty()) registry.byLinkKey.remove(old.linkKey);
    }
    public static Collection<Entry> findByKey(World world, String key) {
        DimensionSpeakers registry = WORLDS.get(world);
        Map<Long, Entry> group = registry == null ? null : registry.byLinkKey.get(normalize(key));
        return group == null || normalize(key).isEmpty() ? Collections.<Entry>emptyList() : new ArrayList<>(group.values());
    }
    public static void clear(World world) { WORLDS.remove(world); }
    public static Entry at(World world, long position) {
        DimensionSpeakers registry = WORLDS.get(world);
        return registry == null ? null : registry.byPosition.get(position);
    }
    public static void clear() { WORLDS.clear(); }
}
