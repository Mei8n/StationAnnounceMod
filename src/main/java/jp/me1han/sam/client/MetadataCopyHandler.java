package jp.me1han.sam.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import jp.me1han.sam.render.TileEntitySpeaker;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import org.lwjgl.input.Keyboard;

public class MetadataCopyHandler {

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        if (event.button == 2 && event.buttonstate && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer player = mc.thePlayer;
            if (player == null) return;

            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                TileEntity te = mc.theWorld.getTileEntity(mc.objectMouseOver.blockX, mc.objectMouseOver.blockY, mc.objectMouseOver.blockZ);

                if (te instanceof TileEntitySpeaker) {
                    ItemStack stack = te.getBlockType().getPickBlock(mc.objectMouseOver, mc.theWorld, te.xCoord, te.yCoord, te.zCoord);

                    if (stack != null) {
                        NBTTagCompound teNbt = new NBTTagCompound();
                        te.writeToNBT(teNbt);

                        NBTTagCompound stackTag = new NBTTagCompound();
                        stackTag.setTag("BlockEntityTag", teNbt);
                        stack.setTagCompound(stackTag);

                        stack.setStackDisplayName(stack.getDisplayName() + " §b(+NBT)");

                        int slot = player.inventory.currentItem;
                        player.inventory.mainInventory[slot] = stack;

                        if (player.capabilities.isCreativeMode) {
                            mc.playerController.sendSlotPacket(stack, 36 + slot);
                        }

                        player.addChatMessage(new ChatComponentText("§a[SAM]§f Speaker data copied!"));
                        event.setCanceled(true);
                    }
                }
            }
        }
    }
}
