package jp.me1han.sam.client;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import jp.me1han.sam.SpeakerRegistry;
import jp.me1han.sam.render.TileEntitySpeaker;
import net.minecraft.world.World;

/** Client-side index of Speakers whose server configuration has been received. */
public final class ClientSpeakerRegistry {
    private static final Map<World, DimensionSpeakers> WORLDS = new IdentityHashMap<>();

    private static final class DimensionSpeakers {
        final Map<Long, Entry> byPosition = new HashMap<>();
        final Map<String, Map<Long, TileEntitySpeaker>> byLinkKey = new HashMap<>();
    }

    private static final class Entry {
        final TileEntitySpeaker tile;
        final String linkKey;

        Entry(TileEntitySpeaker tile) {
            this.tile = tile;
            this.linkKey = SpeakerRegistry.normalize(tile.linkKey);
        }
    }

    private ClientSpeakerRegistry() {}

    public static void register(TileEntitySpeaker tile) {
        World world = tile.getWorldObj();
        if (world == null || !world.isRemote || tile.isInvalid() || !tile.isClientConfigSynced()) return;
        DimensionSpeakers registry = WORLDS.computeIfAbsent(world, key -> new DimensionSpeakers());
        long position = SpeakerRegistry.position(tile.xCoord, tile.yCoord, tile.zCoord);
        Entry old = registry.byPosition.get(position);
        String linkKey = SpeakerRegistry.normalize(tile.linkKey);
        if (old != null && old.tile == tile && old.linkKey.equals(linkKey)) return;
        if (old != null) remove(registry, position, old);
        Entry entry = new Entry(tile);
        registry.byPosition.put(position, entry);
        registry.byLinkKey.computeIfAbsent(linkKey, key -> new HashMap<>()).put(position, tile);
    }

    public static void unregister(TileEntitySpeaker tile) {
        World world = tile.getWorldObj();
        DimensionSpeakers registry = WORLDS.get(world);
        if (registry == null) return;
        long position = SpeakerRegistry.position(tile.xCoord, tile.yCoord, tile.zCoord);
        Entry old = registry.byPosition.get(position);
        if (old != null && old.tile == tile) remove(registry, position, old);
        if (registry.byPosition.isEmpty()) WORLDS.remove(world);
    }

    private static void remove(DimensionSpeakers registry, long position, Entry old) {
        registry.byPosition.remove(position);
        Map<Long, TileEntitySpeaker> group = registry.byLinkKey.get(old.linkKey);
        if (group == null) return;
        group.remove(position);
        if (group.isEmpty()) registry.byLinkKey.remove(old.linkKey);
    }

    public static Collection<TileEntitySpeaker> findByKey(World world, String key) {
        String normalized = SpeakerRegistry.normalize(key);
        DimensionSpeakers registry = WORLDS.get(world);
        Map<Long, TileEntitySpeaker> group = registry == null ? null : registry.byLinkKey.get(normalized);
        if (group == null || normalized.isEmpty()) return Collections.emptyList();
        return group.values();
    }

    public static TileEntitySpeaker at(World world, long position) {
        DimensionSpeakers registry = WORLDS.get(world);
        Entry entry = registry == null ? null : registry.byPosition.get(position);
        return entry == null ? null : entry.tile;
    }

    public static void clear(World world) { if (world != null) WORLDS.remove(world); }
    public static void clear() { WORLDS.clear(); }
}
