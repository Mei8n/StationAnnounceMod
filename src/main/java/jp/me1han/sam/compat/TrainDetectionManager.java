package jp.me1han.sam.compat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

/** Logical-server, lazy per-World train index rebuilt at most once per game tick. */
public final class TrainDetectionManager {
    public static final TrainDetectionManager INSTANCE = new TrainDetectionManager();
    public static final int CELL_SIZE = 16;

    private static final Map<World, WorldIndex> WORLDS = new IdentityHashMap<>();

    private static final class Record {
        final Entity entity;
        final TrainSnapshot snapshot;
        final int entityId;
        final AxisAlignedBB bounds;
        Boolean controlCar;
        Long formationId;

        Record(Entity entity, TrainSnapshot snapshot, AxisAlignedBB bounds) {
            this.entity = entity;
            this.snapshot = snapshot;
            this.entityId = entity.getEntityId();
            this.bounds = bounds;
        }

        boolean isControlCar() {
            if (controlCar == null) controlCar = snapshot.isControlCar();
            return controlCar;
        }

        long formationId() {
            if (formationId == null) formationId = snapshot.getFormationId();
            return formationId;
        }
    }

    private static final class WorldIndex {
        long epoch = Long.MIN_VALUE;
        boolean built;
        final Map<Long, List<Record>> cells = new HashMap<>();
    }

    private TrainDetectionManager() {}

    public static TrainSnapshot findFirstTrain(World world, AxisAlignedBB bounds, boolean controlCarOnly) {
        Record record = findFirst(world, bounds, controlCarOnly);
        return record == null ? null : record.snapshot;
    }

    public static long findFirstFormationId(World world, AxisAlignedBB bounds, boolean controlCarOnly) {
        Record record = findFirst(world, bounds, controlCarOnly);
        return record == null ? -1L : record.formationId();
    }

    private static Record findFirst(World world, AxisAlignedBB bounds, boolean controlCarOnly) {
        if (world == null || world.isRemote || bounds == null || !finite(bounds)) return null;
        WorldIndex index = WORLDS.get(world);
        if (index == null) {
            index = new WorldIndex();
            WORLDS.put(world, index);
        }
        long epoch = world.getTotalWorldTime();
        if (!index.built || index.epoch != epoch) rebuild(world, index, epoch);

        Map<Integer, Record> candidates = new HashMap<>();
        int minX = cell(bounds.minX), maxX = cell(bounds.maxX);
        int minZ = cell(bounds.minZ), maxZ = cell(bounds.maxZ);
        for (int cellX = minX; cellX <= maxX; cellX++) for (int cellZ = minZ; cellZ <= maxZ; cellZ++) {
            List<Record> records = index.cells.get(cellKey(cellX, cellZ));
            if (records == null) continue;
            for (Record record : records) candidates.put(record.entityId, record);
        }

        Record selected = null;
        double centerX = (bounds.minX + bounds.maxX) * 0.5D;
        double centerY = (bounds.minY + bounds.maxY) * 0.5D;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        double selectedDistance = Double.POSITIVE_INFINITY;
        for (Record record : candidates.values()) {
            if (!record.bounds.intersectsWith(bounds) || controlCarOnly && !record.isControlCar()) continue;
            double dx = (record.bounds.minX + record.bounds.maxX) * 0.5D - centerX;
            double dy = (record.bounds.minY + record.bounds.maxY) * 0.5D - centerY;
            double dz = (record.bounds.minZ + record.bounds.maxZ) * 0.5D - centerZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (selected == null || distance < selectedDistance
                || distance == selectedDistance && record.entityId < selected.entityId) {
                selected = record;
                selectedDistance = distance;
            }
        }
        return selected;
    }

    private static void rebuild(World world, WorldIndex index, long epoch) {
        index.cells.clear();
        TrainCompat compat = TrainCompatRegistry.get();
        if (compat.isAvailable()) for (Object value : world.loadedEntityList) {
            if (!(value instanceof Entity)) continue;
            Entity entity = (Entity)value;
            if (entity.isDead) continue;
            TrainSnapshot snapshot;
            try {
                snapshot = compat.wrap(entity);
            } catch (RuntimeException ignored) {
                continue;
            } catch (LinkageError ignored) {
                continue;
            }
            AxisAlignedBB source = entity.boundingBox;
            if (snapshot == null || source == null || !finite(source)) continue;
            AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(
                source.minX, source.minY, source.minZ, source.maxX, source.maxY, source.maxZ);
            Record record = new Record(entity, snapshot, bounds);
            int minX = cell(bounds.minX), maxX = cell(bounds.maxX);
            int minZ = cell(bounds.minZ), maxZ = cell(bounds.maxZ);
            for (int cellX = minX; cellX <= maxX; cellX++) for (int cellZ = minZ; cellZ <= maxZ; cellZ++)
                index.cells.computeIfAbsent(cellKey(cellX, cellZ), ignored -> new ArrayList<>()).add(record);
        }
        index.epoch = epoch;
        index.built = true;
    }

    private static boolean finite(AxisAlignedBB bounds) {
        return finite(bounds.minX) && finite(bounds.minY) && finite(bounds.minZ)
            && finite(bounds.maxX) && finite(bounds.maxY) && finite(bounds.maxZ);
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static int cell(double coordinate) { return (int)Math.floor(coordinate / CELL_SIZE); }
    private static long cellKey(int x, int z) { return ((long)x << 32) ^ ((long)z & 0xffffffffL); }

    @SubscribeEvent public void unload(WorldEvent.Unload event) { clear(event.world); }
    public static void clear(World world) { WORLDS.remove(world); }
    public static void clear() { WORLDS.clear(); }
}
