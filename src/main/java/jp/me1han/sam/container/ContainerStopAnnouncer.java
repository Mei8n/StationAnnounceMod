package jp.me1han.sam.container;

import jp.me1han.sam.render.TileEntityStopAnnouncer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public class ContainerStopAnnouncer extends Container {
    private TileEntityStopAnnouncer tile;

    public ContainerStopAnnouncer(TileEntityStopAnnouncer tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return player.getDistanceSq((double)tile.xCoord + 0.5D, (double)tile.yCoord + 0.5D, (double)tile.zCoord + 0.5D) <= 64.0D;
    }
}
