package jp.me1han.sam.container;

import jp.me1han.sam.render.TileEntityDepartureMelody;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public class ContainerDepartureMelody extends Container {
    private final TileEntityDepartureMelody tile;

    public ContainerDepartureMelody(TileEntityDepartureMelody tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.tile != null && this.tile.getWorldObj() != null
            && this.tile.getWorldObj().getTileEntity(this.tile.xCoord, this.tile.yCoord, this.tile.zCoord) == this.tile
            && player.getDistanceSq(this.tile.xCoord + 0.5D, this.tile.yCoord + 0.5D, this.tile.zCoord + 0.5D) <= 64.0D;
    }
}
