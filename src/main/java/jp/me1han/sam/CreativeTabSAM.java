package jp.me1han.sam;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
    @SideOnly(Side.CLIENT)
    public void displayAllReleventItems(List list) {
        super.displayAllReleventItems(list);

        Collections.sort(list, new Comparator<ItemStack>() {
            @Override
            public int compare(ItemStack stack1, ItemStack stack2) {
                return Integer.compare(getOrder(stack1.getItem()), getOrder(stack2.getItem()));
            }

            //アイテムの表示順 数値が小さいほど左上に表示
            private int getOrder(Item item) {
                if (item == Item.getItemFromBlock(StationAnnounceModCore.blockAnnouncer)) return 1;
                if (item == Item.getItemFromBlock(StationAnnounceModCore.blockSpeaker)) return 2;
                if (item == Item.getItemFromBlock(StationAnnounceModCore.blockStartAnnouncer)) return 3;
                if (item == Item.getItemFromBlock(StationAnnounceModCore.blockStopAnnouncer)) return 4;
                if (item == Item.getItemFromBlock(StationAnnounceModCore.blockTrainTypeSelector)) return 5;

                return 100;
            }
        });
    }

    @Override
    public String getTranslatedTabLabel() {
        return "itemGroup.sam_tab";
    }
}
