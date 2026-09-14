package jp.me1han.sam.container;

import jp.me1han.sam.render.TileEntityStationNameAnnouncer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public final class ContainerStationNameAnnouncer extends Container {
    private final TileEntityStationNameAnnouncer tile;
    public ContainerStationNameAnnouncer(TileEntityStationNameAnnouncer tile) { this.tile = tile; }
    @Override public boolean canInteractWith(EntityPlayer player) { return tile.isUseableByPlayer(player); }
}
