package jp.me1han.sam.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import jp.me1han.sam.render.TileEntityTrainTypeSelector;

public class ContainerTrainTypeSelector extends Container {
    private TileEntityTrainTypeSelector tile;

    public ContainerTrainTypeSelector(TileEntityTrainTypeSelector tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        // ブロックから離れすぎたら閉じる判定
        return tile.isUseableByPlayer(player);
    }
}
