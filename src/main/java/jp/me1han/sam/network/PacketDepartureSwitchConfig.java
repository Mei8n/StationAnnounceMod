package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.DepartureEvents;
import jp.me1han.sam.container.ContainerDepartureSwitch;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

public class PacketDepartureSwitchConfig implements IMessage {
    public int x, y, z;
    public String linkKey;
    public String modelName;
    public int rotationYaw;
    public PacketDepartureSwitchConfig() {}
    public PacketDepartureSwitchConfig(int x, int y, int z, String key, String modelName, int rotationYaw) {
        this.x = x; this.y = y; this.z = z; this.linkKey = key; this.modelName = modelName; this.rotationYaw = rotationYaw;
    }
    @Override public void fromBytes(ByteBuf buf) {
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt(); linkKey = ByteBufUtils.readUTF8String(buf);
        modelName = ByteBufUtils.readUTF8String(buf); rotationYaw = buf.readInt();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); ByteBufUtils.writeUTF8String(buf, linkKey);
        ByteBufUtils.writeUTF8String(buf, modelName); buf.writeInt(rotationYaw);
    }
    public static class Handler implements IMessageHandler<PacketDepartureSwitchConfig, IMessage> {
        @Override public IMessage onMessage(final PacketDepartureSwitchConfig msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            DepartureEvents.INSTANCE.enqueue(() -> {
                // The wire type only accepts integers; applyConfig normalizes even out-of-range values.
                if (player.isDead || msg.linkKey.length() > 64 || msg.modelName.length() > 128
                    || jp.me1han.sam.switchmodel.SwitchModelRegistry.get(msg.modelName) == null
                    || !player.worldObj.blockExists(msg.x, msg.y, msg.z)) return;
                TileEntity tile = player.worldObj.getTileEntity(msg.x, msg.y, msg.z);
                if (new ContainerDepartureSwitch(tile).canInteractWith(player)
                    && player.canPlayerEdit(msg.x, msg.y, msg.z, 1, player.getHeldItem())) {
                    ((jp.me1han.sam.render.TileEntityDepartureSwitch) tile).applyConfig(msg.linkKey, msg.modelName, msg.rotationYaw);
                }
            });
            return null;
        }
    }
}
