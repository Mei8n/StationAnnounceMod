package jp.me1han.sam.link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Server-only index for logical links between loaded SAM devices. */
public final class SamLinkRegistry {
    private static final Map<World, WorldLinks> WORLDS = new IdentityHashMap<World, WorldLinks>();

    private static final class WorldLinks {
        final Map<String, LinkedHashSet<TileEntity>> byKey = new HashMap<String, LinkedHashSet<TileEntity>>();
        final IdentityHashMap<TileEntity, String> keyByTile = new IdentityHashMap<TileEntity, String>();
        final Map<Long, TileEntity> byPosition = new HashMap<Long, TileEntity>();
    }

    private SamLinkRegistry() {}

    public static void register(TileEntity tile) {
        if (!(tile instanceof SamLinkedTile)) return;
        World world = tile.getWorldObj();
        if (world == null || world.isRemote || tile.isInvalid()) return;
        String key = LinkKey.normalize(((SamLinkedTile) tile).getLinkKey());
        if (key.isEmpty()) {
            unregister(tile);
            return;
        }

        WorldLinks links = WORLDS.get(world);
        if (links == null) {
            links = new WorldLinks();
            WORLDS.put(world, links);
        }

        long position = position(tile.xCoord, tile.yCoord, tile.zCoord);
        TileEntity replaced = links.byPosition.get(position);
        if (replaced != null && replaced != tile) remove(links, replaced);
        links.byPosition.put(position, tile);
        reindex(links, tile, key);
    }

    public static void unregister(TileEntity tile) {
        if (tile == null || tile.getWorldObj() == null || tile.getWorldObj().isRemote) return;
        WorldLinks links = WORLDS.get(tile.getWorldObj());
        if (links == null) return;
        remove(links, tile);
        if (links.keyByTile.isEmpty()) WORLDS.remove(tile.getWorldObj());
    }

    /** Re-indexes a loaded tile immediately after a logical link-key change. */
    public static void reindex(TileEntity tile) {
        if (!(tile instanceof SamLinkedTile)) return;
        World world = tile.getWorldObj();
        if (world == null || world.isRemote || tile.isInvalid()) return;
        String key = LinkKey.normalize(((SamLinkedTile) tile).getLinkKey());
        if (key.isEmpty()) {
            unregister(tile);
            return;
        }
        WorldLinks links = WORLDS.get(world);
        if (links == null || !links.keyByTile.containsKey(tile)) {
            register(tile);
            return;
        }
        reindex(links, tile, key);
    }

    private static void reindex(WorldLinks links, TileEntity tile, String newKey) {
        String oldKey = links.keyByTile.get(tile);
        if (newKey.equals(oldKey)) return;
        if (oldKey != null) removeFromKey(links, oldKey, tile);
        LinkedHashSet<TileEntity> group = links.byKey.get(newKey);
        if (group == null) {
            group = new LinkedHashSet<TileEntity>();
            links.byKey.put(newKey, group);
        }
        group.add(tile);
        links.keyByTile.put(tile, newKey);
    }

    private static void remove(WorldLinks links, TileEntity tile) {
        String key = links.keyByTile.remove(tile);
        if (key != null && !key.isEmpty()) removeFromKey(links, key, tile);
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
        Iterator<TileEntity> iterator = group.iterator();
        while (iterator.hasNext()) {
            TileEntity tile = iterator.next();
            if (!usable(world, tile)) {
                iterator.remove();
                removeStale(world, tile);
            } else if (type.isInstance(tile)) {
                return type.cast(tile);
            }
        }
        cleanupEmpty(world, LinkKey.normalize(key), group);
        return null;
    }

    public static <T extends TileEntity> List<T> findAll(World world, String key, Class<T> type) {
        LinkedHashSet<TileEntity> group = group(world, key);
        if (group == null) return Collections.emptyList();
        List<T> result = null;
        Iterator<TileEntity> iterator = group.iterator();
        while (iterator.hasNext()) {
            TileEntity tile = iterator.next();
            if (!usable(world, tile)) {
                iterator.remove();
                removeStale(world, tile);
            } else if (type.isInstance(tile)) {
                if (result == null) result = new ArrayList<T>();
                result.add(type.cast(tile));
            }
        }
        cleanupEmpty(world, LinkKey.normalize(key), group);
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

    private static void removeStale(World world, TileEntity tile) {
        WorldLinks links = WORLDS.get(world);
        if (links == null) return;
        String key = links.keyByTile.remove(tile);
        long position = position(tile.xCoord, tile.yCoord, tile.zCoord);
        if (links.byPosition.get(position) == tile) links.byPosition.remove(position);
        // The caller already removed the tile from the current key group.
        if (key != null && links.keyByTile.isEmpty()) WORLDS.remove(world);
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
