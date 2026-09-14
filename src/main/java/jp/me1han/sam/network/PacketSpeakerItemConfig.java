package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.item.ItemSpeaker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** Applies a validated Speaker model selection to the exact held inventory slot. */
public class PacketSpeakerItemConfig implements IMessage {
    public int slot;
    public String modelName;

    public PacketSpeakerItemConfig() {}
    public PacketSpeakerItemConfig(int slot, String modelName) {
        this.slot = slot;
        this.modelName = modelName;
    }
    @Override public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        modelName = PacketLimits.readString(buf, PacketLimits.MODEL);
        PacketLimits.requireDecoded(isValidPayload(), "Invalid Speaker item config payload");
    }
    public boolean isValidPayload() {
        return PacketLimits.slot(slot) && PacketLimits.string(PacketLimits.normalize(modelName), PacketLimits.MODEL);
    }
    @Override public void toBytes(ByteBuf buf) {
        PacketLimits.require(isValidPayload(), "Invalid Speaker item config payload");
        buf.writeInt(slot);
        PacketLimits.writeString(buf, modelName, PacketLimits.MODEL);
    }

    public static class Handler implements IMessageHandler<PacketSpeakerItemConfig, IMessage> {
        @Override public IMessage onMessage(PacketSpeakerItemConfig message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final net.minecraft.network.NetHandlerPlayServer connection = ctx.getServerHandler();
            final int slot = message.slot;
            final String modelName = PacketLimits.normalize(message.modelName);
            ServerTaskQueue.INSTANCE.enqueue(player, () -> {
                if (player.isDead || player.playerNetServerHandler != connection
                    || !connection.netManager.isChannelOpen() || !message.isValidPayload()
                    || player.inventory.currentItem != slot) return;
                ItemStack stack = player.inventory.getStackInSlot(slot);
                if (!ItemSpeaker.isSpeakerItem(stack)) return;
                if (ItemSpeaker.selectModel(stack, modelName)) {
                    player.inventory.markDirty();
                    player.inventoryContainer.detectAndSendChanges();
                }
            });
            return null;
        }
    }
}
