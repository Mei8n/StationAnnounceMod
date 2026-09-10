package jp.me1han.sam.compat;

import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import jp.me1han.sam.StationAnnounceModCore;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/** Discovers optional train integrations without linking SAM core to their APIs. */
public final class TrainCompatRegistry {
    private static final TrainCompat NONE = new TrainCompat() {
        @Override public String getId() { return "none"; }
        @Override public boolean isAvailable() { return false; }
        @Override public List<TrainSnapshot> findTrains(World world, AxisAlignedBB bounds) {
            return Collections.emptyList();
        }
        @Override public TrainSnapshot wrap(Entity entity) { return null; }
        @Override public boolean isInspectionTool(ItemStack stack) { return false; }
    };

    private static TrainCompat active = NONE;

    private TrainCompatRegistry() {}

    public static synchronized void initialize() {
        active = NONE;
        try {
            ServiceLoader<TrainCompat> loader = ServiceLoader.load(TrainCompat.class, TrainCompat.class.getClassLoader());
            for (TrainCompat candidate : loader) {
                if (candidate.isAvailable()) {
                    active = candidate;
                    StationAnnounceModCore.logger.info("Enabled optional train integration: {}", candidate.getId());
                    return;
                }
            }
        } catch (Throwable error) {
            StationAnnounceModCore.logger.warn("Could not initialize an optional train integration", error);
        }
    }

    public static TrainCompat get() {
        return active;
    }
}
