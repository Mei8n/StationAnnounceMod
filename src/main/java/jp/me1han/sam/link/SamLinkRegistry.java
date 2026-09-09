package jp.me1han.sam.link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Server-only index for logical links between loaded SAM devices. */
public final class SamLinkRegistry {
    private static final Map<World, WorldLinks> WORLDS = new IdentityHashMap<World, WorldLinks>();

    private static final class WorldLinks {
        final Map<String, LinkedHashSet<TileEntity>> byKey = new HashMap<String, LinkedHashSet<TileEntity>>();
        final IdentityHashMap<TileEntity, String> keyByTile = new IdentityHashMap<TileEntity, String>();
        final IdentityHashMap<TileEntity, Long> orderByTile = new IdentityHashMap<TileEntity, Long>();
        final Map<Long, TileEntity> byPosition = new HashMap<Long, TileEntity>();
        long nextOrder;
    }

    private SamLinkRegistry() {}

    public static void register(TileEntity tile) {
        if (!(tile instanceof SamLinkedTile)) return;
        World world = tile.getWorldObj();
        if (world == null || world.isRemote || tile.isInvalid()) return;
        String key = LinkKey.normalize(((SamLinkedTile) tile).getLinkKey());

        WorldLinks links = WORLDS.get(world);
        if (links == null) {
            links = new WorldLinks();
            WORLDS.put(world, links);
        }

        long position = position(tile.xCoord, tile.yCoord, tile.zCoord);
        TileEntity replaced = links.byPosition.get(position);
        if (replaced != null && replaced != tile) remove(links, replaced);
        links.byPosition.put(position, tile);
        if (!links.orderByTile.containsKey(tile)) links.orderByTile.put(tile, links.nextOrder++);
        reindex(links, tile, key);
    }

    public static void unregister(TileEntity tile) {
        if (tile == null || tile.getWorldObj() == null || tile.getWorldObj().isRemote) return;
        WorldLinks links = WORLDS.get(tile.getWorldObj());
        if (links == null) return;
        remove(links, tile);
        if (links.orderByTile.isEmpty()) WORLDS.remove(tile.getWorldObj());
    }

    /** Re-indexes a loaded tile immediately after a logical link-key change. */
    public static void reindex(TileEntity tile) {
        if (!(tile instanceof SamLinkedTile)) return;
        World world = tile.getWorldObj();
        if (world == null || world.isRemote || tile.isInvalid()) return;
        String key = LinkKey.normalize(((SamLinkedTile) tile).getLinkKey());
        WorldLinks links = WORLDS.get(world);
        if (links == null || !links.orderByTile.containsKey(tile)) {
            register(tile);
            return;
        }
        reindex(links, tile, key);
    }

    private static void reindex(WorldLinks links, TileEntity tile, String newKey) {
        String oldKey = links.keyByTile.get(tile);
        if (newKey.equals(oldKey)) return;
        if (oldKey != null) removeFromKey(links, oldKey, tile);
        links.keyByTile.put(tile, newKey);
        if (newKey.isEmpty()) return;
        LinkedHashSet<TileEntity> group = links.byKey.get(newKey);
        if (group == null) {
            group = new LinkedHashSet<TileEntity>();
            links.byKey.put(newKey, group);
        }
        group.add(tile);
    }

    private static void remove(WorldLinks links, TileEntity tile) {
        String key = links.keyByTile.remove(tile);
        if (key != null && !key.isEmpty()) removeFromKey(links, key, tile);
        links.orderByTile.remove(tile);
        long position = position(tile.xCoord, tile.yCoord, tile.zCoord);
        if (links.byPosition.get(position) == tile) links.byPosition.remove(position);
    }

    private static void removeFromKey(WorldLinks links, String key, TileEntity tile) {
        LinkedHashSet<TileEntity> group = links.byKey.get(key);
        if (group == null) return;
        group.remove(tile);
        if (group.isEmpty()) links.byKey.remove(key);
    }

    public static <T extends TileEntity> T findFirst(World world, String key, Class<T> type) {
        LinkedHashSet<TileEntity> group = group(world, key);
        if (group == null) return null;
        WorldLinks links = links(world);
        Iterator<TileEntity> iterator = group.iterator();
        T result = null;
        long firstOrder = Long.MAX_VALUE;
        while (iterator.hasNext()) {
            TileEntity tile = iterator.next();
            if (!usable(world, tile)) {
                iterator.remove();
                removeStale(links, tile);
            } else if (type.isInstance(tile)) {
                long order = order(links, tile);
                if (result == null || order < firstOrder) {
                    result = type.cast(tile);
                    firstOrder = order;
                }
            }
        }
        cleanupEmpty(world, LinkKey.normalize(key), group);
        cleanupWorld(world);
        return result;
    }

    public static <T extends TileEntity> List<T> findAll(World world, String key, Class<T> type) {
        LinkedHashSet<TileEntity> group = group(world, key);
        if (group == null) return Collections.emptyList();
        final WorldLinks links = links(world);
        List<T> result = null;
        Iterator<TileEntity> iterator = group.iterator();
        while (iterator.hasNext()) {
            TileEntity tile = iterator.next();
            if (!usable(world, tile)) {
                iterator.remove();
                removeStale(links, tile);
            } else if (type.isInstance(tile)) {
                if (result == null) result = new ArrayList<T>();
                result.add(type.cast(tile));
            }
        }
        cleanupEmpty(world, LinkKey.normalize(key), group);
        cleanupWorld(world);
        if (result != null && result.size() > 1) {
            Collections.sort(result, new Comparator<T>() {
                @Override public int compare(T a, T b) {
                    return Long.compare(order(links, a), order(links, b));
                }
            });
        }
        return result == null ? Collections.<T>emptyList() : result;
    }

    public static TileEntity at(World world, int x, int y, int z) {
        WorldLinks links = WORLDS.get(world);
        TileEntity tile = links == null ? null : links.byPosition.get(position(x, y, z));
        return usable(world, tile) ? tile : null;
    }

    private static LinkedHashSet<TileEntity> group(World world, String key) {
        String normalized = LinkKey.normalize(key);
        if (world == null || world.isRemote || normalized.isEmpty()) return null;
        WorldLinks links = WORLDS.get(world);
        return links == null ? null : links.byKey.get(normalized);
    }

    private static boolean usable(World world, TileEntity tile) {
        return tile != null && tile.getWorldObj() == world && !tile.isInvalid();
    }

    private static WorldLinks links(World world) { return WORLDS.get(world); }

    private static long order(WorldLinks links, TileEntity tile) {
        Long value = links == null ? null : links.orderByTile.get(tile);
        return value == null ? Long.MAX_VALUE : value.longValue();
    }

    private static void removeStale(WorldLinks links, TileEntity tile) {
        if (links == null) return;
        links.keyByTile.remove(tile);
        links.orderByTile.remove(tile);
        long position = position(tile.xCoord, tile.yCoord, tile.zCoord);
        if (links.byPosition.get(position) == tile) links.byPosition.remove(position);
        // The caller already removed the tile from the current key group.
    }

    private static void cleanupWorld(World world) {
        WorldLinks links = WORLDS.get(world);
        if (links != null && links.orderByTile.isEmpty()) WORLDS.remove(world);
    }

    private static void cleanupEmpty(World world, String key, LinkedHashSet<TileEntity> group) {
        if (!group.isEmpty()) return;
        WorldLinks links = WORLDS.get(world);
        if (links != null && links.byKey.get(key) == group) links.byKey.remove(key);
    }

    private static long position(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (y & 0xfffL);
    }

    public static void clear(World world) { WORLDS.remove(world); }
    public static void clear() { WORLDS.clear(); }
}
