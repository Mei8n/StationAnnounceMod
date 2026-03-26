package jp.me1han.sam.container;

import jp.me1han.sam.render.TileEntityDebugReceiver;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public class ContainerDebugReceiver extends Container {
    private TileEntityDebugReceiver tile;

    public ContainerDebugReceiver(TileEntityDebugReceiver tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
