package jp.me1han.sam.block;

import jp.me1han.sam.StationAnnounceModCore;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockTrainTypeSelector extends Block {

    public BlockTrainTypeSelector() {
        super(Material.iron);
        this.setBlockName("trainTypeSelector");
        this.setBlockTextureName("stationannouncemod:train_type_selector");
        this.setHardness(2.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeMetal);
        // 確定済みのクリエイティブタブに登録
        this.setCreativeTab(StationAnnounceModCore.tabSAM);
    }
}
