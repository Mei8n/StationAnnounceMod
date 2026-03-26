package jp.me1han.sam.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import jp.me1han.sam.render.TileEntityAnnouncer;

public class ContainerAnnouncer extends Container {
    private TileEntityAnnouncer tile;

    public ContainerAnnouncer(TileEntityAnnouncer tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUseableByPlayer(player);
    }
}
