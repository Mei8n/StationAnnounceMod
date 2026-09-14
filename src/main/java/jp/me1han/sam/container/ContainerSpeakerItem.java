package jp.me1han.sam.container;

import jp.me1han.sam.item.ItemSpeaker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/** Keeps the item picker open only while the same Speaker slot remains selected. */
public class ContainerSpeakerItem extends Container {
    private final EntityPlayer player;
    private final int slot;

    public ContainerSpeakerItem(EntityPlayer player) {
        this.player = player;
        this.slot = player.inventory.currentItem;
    }

    @Override public boolean canInteractWith(EntityPlayer candidate) {
        return candidate == player && candidate.inventory.currentItem == slot
            && ItemSpeaker.isSpeakerItem(candidate.getHeldItem());
    }
}
