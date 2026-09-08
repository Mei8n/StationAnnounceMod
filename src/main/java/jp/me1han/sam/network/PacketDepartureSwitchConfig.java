package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
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
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt(); linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        modelName = PacketLimits.readString(buf, PacketLimits.MODEL); rotationYaw = buf.readInt();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); ByteBufUtils.writeUTF8String(buf, linkKey);
        ByteBufUtils.writeUTF8String(buf, modelName); buf.writeInt(rotationYaw);
    }
    public static class Handler implements IMessageHandler<PacketDepartureSwitchConfig, IMessage> {
        @Override public IMessage onMessage(PacketDepartureSwitchConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, jp.me1han.sam.render.TileEntityDepartureSwitch.class, tile -> {
                if (!ConfigAccess.key(m.linkKey) || !PacketLimits.string(m.modelName, PacketLimits.MODEL)
                    || jp.me1han.sam.switchmodel.SwitchModelRegistry.get(m.modelName) == null) return;
                int yaw = (int) jp.me1han.sam.switchmodel.SwitchYaw.normalize(m.rotationYaw);
                if (ConfigAccess.normalize(m.linkKey).equals(tile.linkKey) && m.modelName.equals(tile.modelName)
                    && yaw == tile.getRotationYaw()) return;
                tile.applyConfig(m.linkKey, m.modelName, yaw);
            }); return null;
        }
    }
}
