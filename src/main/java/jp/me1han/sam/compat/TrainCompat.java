package jp.me1han.sam.compat;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Optional train-mod integration point used by SAM core.
 *
 * Implementations must not expose classes from the optional mod in this API.
 */
public interface TrainCompat {
    String getId();

    boolean isAvailable();

    /**
     * Returns the first matching train, or {@code null}. Only the selected
     * entity may be wrapped.
     */
    TrainSnapshot findFirstTrain(World world, AxisAlignedBB bounds, boolean controlCarOnly);

    /**
     * Returns the first matching train's formation id without creating a
     * snapshot, or {@code -1} when no train matches.
     */
    long findFirstFormationId(World world, AxisAlignedBB bounds, boolean controlCarOnly);

    TrainSnapshot wrap(Entity entity);

    boolean isInspectionTool(ItemStack stack);
}
