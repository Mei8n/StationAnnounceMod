package jp.me1han.sam.compat;

import java.util.List;
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

    List<TrainSnapshot> findTrains(World world, AxisAlignedBB bounds);

    TrainSnapshot wrap(Entity entity);

    boolean isInspectionTool(ItemStack stack);
}
