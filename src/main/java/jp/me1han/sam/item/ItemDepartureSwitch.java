package jp.me1han.sam.item;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.switchmodel.SwitchModelRegistry;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** The switch block item keeps its selected model in portable block-entity metadata. */
public class ItemDepartureSwitch extends ItemBlock {
    public ItemDepartureSwitch(Block block) { super(block); }

    @Override public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            player.openGui(StationAnnounceModCore.instance, StationAnnounceModCore.GUI_ID_DEPARTURE_SWITCH_ITEM,
                world, 0, 0, 0);
        }
        return stack;
    }

    public static boolean isSwitchItem(ItemStack stack) {
        return stack != null && stack.getItem() == Item.getItemFromBlock(StationAnnounceModCore.blockDepartureSwitch);
    }

    public static String selectedModel(ItemStack stack) {
        if (stack != null && stack.hasTagCompound() && stack.getTagCompound().hasKey("BlockEntityTag")) {
            String name = stack.getTagCompound().getCompoundTag("BlockEntityTag").getString("modelName");
            if (SwitchModelRegistry.get(name) != null) return name;
        }
        return SwitchModelRegistry.DEFAULT_MODEL;
    }

    public static boolean selectModel(ItemStack stack, String name) {
        if (stack == null || SwitchModelRegistry.get(name) == null) return false;
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound block = root.hasKey("BlockEntityTag")
            ? root.getCompoundTag("BlockEntityTag") : new NBTTagCompound();
        if (name.equals(block.getString("modelName"))) return false;
        block.setString("modelName", name);
        root.setTag("BlockEntityTag", block);
        stack.setTagCompound(root);
        return true;
    }
}
