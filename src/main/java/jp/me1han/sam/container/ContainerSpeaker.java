package jp.me1han.sam.container;

import jp.me1han.sam.render.TileEntitySpeaker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public class ContainerSpeaker extends Container {
    private final TileEntitySpeaker tile;
    public ContainerSpeaker(TileEntitySpeaker tile) { this.tile = tile; }
    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return player.getDistanceSq(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5) <= 64.0;
    }
}
