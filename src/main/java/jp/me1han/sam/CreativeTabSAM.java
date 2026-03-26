package jp.me1han.sam;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class CreativeTabSAM extends CreativeTabs {

    public CreativeTabSAM(String label) {
        super(label);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getTabIconItem() {
        return Item.getItemFromBlock(StationAnnounceModCore.blockAnnouncer);
    }

    @Override
    public String getTranslatedTabLabel() {
        return "itemGroup.sam_tab";
    }
}
