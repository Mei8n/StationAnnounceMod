package jp.me1han.sam.container;

import jp.me1han.sam.item.ItemDepartureSwitch;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/** Keeps the item picker open only while the same switch item remains selected. */
public class ContainerDepartureSwitchItem extends Container {
    private final EntityPlayer player;
    private final int slot;

    public ContainerDepartureSwitchItem(EntityPlayer player) {
        this.player = player;
        this.slot = player.inventory.currentItem;
    }

    @Override public boolean canInteractWith(EntityPlayer candidate) {
        return candidate == player && candidate.inventory.currentItem == slot
            && ItemDepartureSwitch.isSwitchItem(candidate.getHeldItem());
    }
}
