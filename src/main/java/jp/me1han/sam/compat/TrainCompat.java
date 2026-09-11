package jp.me1han.sam.compat;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

/**
 * Optional train-mod integration point used by SAM core.
 *
 * Implementations must not expose classes from the optional mod in this API.
 */
public interface TrainCompat {
    String getId();

    boolean isAvailable();

    TrainSnapshot wrap(Entity entity);

    boolean isInspectionTool(ItemStack stack);
}
