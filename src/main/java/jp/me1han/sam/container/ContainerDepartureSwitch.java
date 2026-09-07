package jp.me1han.sam.container;

import jp.me1han.sam.DepartureSwitchLink;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;

public class ContainerDepartureSwitch extends Container {
    private final TileEntity tile;
    public ContainerDepartureSwitch(TileEntity tile) { this.tile = tile; }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return tile != null && DepartureSwitchLink.isSwitch(tile) && !tile.isInvalid()
            && tile.getWorldObj() == player.worldObj
            && player.worldObj.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile
            && player.getDistanceSq(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5) <= 64;
    }
}
