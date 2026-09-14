package jp.me1han.sam.item;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.speakermodel.SpeakerModelRegistry;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Speaker block item with a portable optional model selection. */
public class ItemSpeaker extends ItemBlock {
    public ItemSpeaker(Block block) { super(block); }

    @Override public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            player.openGui(StationAnnounceModCore.instance, StationAnnounceModCore.GUI_ID_SPEAKER_ITEM,
                world, 0, 0, 0);
        }
        return stack;
    }

    public static boolean isSpeakerItem(ItemStack stack) {
        return stack != null && stack.getItem() == Item.getItemFromBlock(StationAnnounceModCore.blockSpeaker);
    }

    public static String selectedModel(ItemStack stack) {
        if (stack != null && stack.hasTagCompound() && stack.getTagCompound().hasKey("BlockEntityTag")) {
            return stack.getTagCompound().getCompoundTag("BlockEntityTag").getString("modelName");
        }
        return "";
    }

    public static boolean selectModel(ItemStack stack, String name) {
        name = name == null ? "" : name;
        if (stack == null || (!name.isEmpty() && SpeakerModelRegistry.get(name) == null)) return false;
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
