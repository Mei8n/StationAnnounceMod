package jp.me1han.sam;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class SAMCreativeTab extends CreativeTabs {

    public SAMCreativeTab(String label) {
        super(label);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getTabIconItem() {
        // タブのアイコンとして「放送装置」を表示
        // BlockからItemを取得して返します
        return Item.getItemFromBlock(StationAnnounceMod.blockAnnouncer);
    }

    @Override
    public String getTranslatedTabLabel() {
        // クリエイティブ画面でマウスを合わせた時に表示される名前
        return "itemGroup.sam_tab";
    }
}
