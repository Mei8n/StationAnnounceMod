package jp.me1han.sam;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;

public class SAMCreativeTab extends CreativeTabs {

    public SAMCreativeTab(String label) {
        super(label);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void displayAllReleventItems(List list) {
        super.displayAllReleventItems(list);

        // RTMが入っていない場合、列車選別装置をリストから削除
        if (!cpw.mods.fml.common.Loader.isModLoaded("RTM")) {
            // Jabel環境で安全に動作するようIterator等を使わずにremoveIfをシミュレート、または型指定
            for (int i = 0; i < list.size(); i++) {
                Object obj = list.get(i);
                if (obj instanceof ItemStack) {
                    ItemStack is = (ItemStack) obj;
                    if (Block.getBlockFromItem(is.getItem()) == StationAnnounceMod.blockTrainSelector) {
                        list.remove(i);
                        i--;
                    }
                }
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getTabIconItem() {
        return Item.getItemFromBlock(StationAnnounceMod.blockAnnouncer);
    }

    @Override
    public String getTranslatedTabLabel() {
        return "itemGroup.sam_tab";
    }
}
