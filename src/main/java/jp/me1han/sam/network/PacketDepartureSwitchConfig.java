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
    public float offsetX, offsetY, offsetZ;
    public PacketDepartureSwitchConfig() {}
    public PacketDepartureSwitchConfig(int x, int y, int z, String key, String modelName, int rotationYaw,
                                       float offsetX, float offsetY, float offsetZ) {
        this.x = x; this.y = y; this.z = z; this.linkKey = key; this.modelName = modelName; this.rotationYaw = rotationYaw;
        this.offsetX = offsetX; this.offsetY = offsetY; this.offsetZ = offsetZ;
    }
    @Override public void fromBytes(ByteBuf buf) {
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt(); linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        modelName = PacketLimits.readString(buf, PacketLimits.MODEL); rotationYaw = buf.readInt();
        offsetX = buf.readFloat(); offsetY = buf.readFloat(); offsetZ = buf.readFloat();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); ByteBufUtils.writeUTF8String(buf, linkKey);
        ByteBufUtils.writeUTF8String(buf, modelName); buf.writeInt(rotationYaw);
        buf.writeFloat(offsetX); buf.writeFloat(offsetY); buf.writeFloat(offsetZ);
    }
    public static class Handler implements IMessageHandler<PacketDepartureSwitchConfig, IMessage> {
        @Override public IMessage onMessage(PacketDepartureSwitchConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, jp.me1han.sam.render.TileEntityDepartureSwitch.class, tile -> {
                if (!ConfigAccess.key(m.linkKey) || !PacketLimits.string(m.modelName, PacketLimits.MODEL)
                    || jp.me1han.sam.switchmodel.SwitchModelRegistry.get(m.modelName) == null
                    || !jp.me1han.sam.render.TileEntityDepartureSwitch.validOffset(m.offsetX)
                    || !jp.me1han.sam.render.TileEntityDepartureSwitch.validOffset(m.offsetY)
                    || !jp.me1han.sam.render.TileEntityDepartureSwitch.validOffset(m.offsetZ)) return;
                int yaw = (int) jp.me1han.sam.switchmodel.SwitchYaw.normalize(m.rotationYaw);
                if (ConfigAccess.normalize(m.linkKey).equals(tile.linkKey) && m.modelName.equals(tile.modelName)
                    && yaw == tile.getRotationYaw() && m.offsetX == tile.getOffsetX()
                    && m.offsetY == tile.getOffsetY() && m.offsetZ == tile.getOffsetZ()) return;
                tile.applyConfig(m.linkKey, m.modelName, yaw, m.offsetX, m.offsetY, m.offsetZ);
            }); return null;
        }
    }
}
