package jp.me1han.sam.container;

import jp.me1han.sam.render.TileEntityStartAnnouncer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public class ContainerStartAnnouncer extends Container {
    private TileEntityStartAnnouncer tile;

    public ContainerStartAnnouncer(TileEntityStartAnnouncer tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
