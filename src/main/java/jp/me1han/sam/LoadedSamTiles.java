package jp.me1han.sam;

import java.util.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Small server-side index of SAM controllers only; no world-wide TE scans or ticking. */
public final class LoadedSamTiles {
    private static final Map<World, Map<Long, TileEntity>> WORLDS = new IdentityHashMap<>();
    public static void register(TileEntity tile) {
        World world = tile.getWorldObj();
        if (world == null || world.isRemote || tile.isInvalid()) return;
        WORLDS.computeIfAbsent(world, w -> new LinkedHashMap<>())
            .put(SpeakerRegistry.position(tile.xCoord, tile.yCoord, tile.zCoord), tile);
    }
    public static void unregister(TileEntity tile) {
        if (tile.getWorldObj() == null || tile.getWorldObj().isRemote) return;
        Map<Long, TileEntity> tiles = WORLDS.get(tile.getWorldObj());
        if (tiles == null) return;
        tiles.remove(SpeakerRegistry.position(tile.xCoord, tile.yCoord, tile.zCoord), tile);
        if (tiles.isEmpty()) WORLDS.remove(tile.getWorldObj());
    }
    public static Collection<TileEntity> all(World world) {
        Map<Long, TileEntity> tiles = WORLDS.get(world);
        return tiles == null ? Collections.<TileEntity>emptyList() : new ArrayList<>(tiles.values());
    }
    public static void clear(World world) { WORLDS.remove(world); }
    public static void clear() { WORLDS.clear(); }
}
