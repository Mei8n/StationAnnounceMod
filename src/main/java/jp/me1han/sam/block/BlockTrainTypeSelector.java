package jp.me1han.sam.block;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.me1han.sam.StationAnnounceModCore;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import java.util.List;

public class BlockTrainTypeSelector extends Block {

    public BlockTrainTypeSelector() {
        super(Material.iron);
        this.setBlockName("trainTypeSelector");
        this.setBlockTextureName("stationannouncemod:train_type_selector");
        this.setHardness(2.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeMetal);
        this.setCreativeTab(StationAnnounceModCore.tabSAM);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        if (Loader.isModLoaded("RTM")) {
            list.add(new net.minecraft.item.ItemStack(item));
        }
    }
}
