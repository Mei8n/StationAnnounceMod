package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.item.ItemDepartureSwitch;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** Applies a picker selection to the exact held stack after server-side validation. */
public class PacketDepartureSwitchItemConfig implements IMessage {
    public int slot;
    public String modelName;

    public PacketDepartureSwitchItemConfig() {}
    public PacketDepartureSwitchItemConfig(int slot, String modelName) {
        this.slot = slot;
        this.modelName = modelName;
    }
    @Override public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        modelName = PacketLimits.readString(buf, PacketLimits.MODEL);
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        ByteBufUtils.writeUTF8String(buf, modelName);
    }

    public static class Handler implements IMessageHandler<PacketDepartureSwitchItemConfig, IMessage> {
        @Override public IMessage onMessage(PacketDepartureSwitchItemConfig message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final net.minecraft.network.NetHandlerPlayServer connection = ctx.getServerHandler();
            final int slot = message.slot;
            final String modelName = message.modelName;
            ServerTaskQueue.INSTANCE.enqueue(() -> {
                if (player.isDead || player.playerNetServerHandler != connection
                    || !connection.netManager.isChannelOpen() || slot < 0 || slot > 8
                    || player.inventory.currentItem != slot
                    || !PacketLimits.string(modelName, PacketLimits.MODEL)) return;
                ItemStack stack = player.inventory.getStackInSlot(slot);
                if (!ItemDepartureSwitch.isSwitchItem(stack)) return;
                if (ItemDepartureSwitch.selectModel(stack, modelName)) {
                    player.inventory.markDirty();
                    player.inventoryContainer.detectAndSendChanges();
                }
            });
            return null;
        }
    }
}
