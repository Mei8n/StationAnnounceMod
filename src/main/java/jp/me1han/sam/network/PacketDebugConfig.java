package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.render.TileEntityDebugReceiver;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class PacketDebugConfig implements IMessage {
    public int x, y, z;
    public String linkKey;

    public PacketDebugConfig() {}

    public PacketDebugConfig(int x, int y, int z, String linkKey) {
        this.x = x; this.y = y; this.z = z; this.linkKey = linkKey;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt();
        linkKey = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        ByteBufUtils.writeUTF8String(buf, this.linkKey != null ? this.linkKey : "");
    }

    public static class Handler implements IMessageHandler<PacketDebugConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketDebugConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);
            if (te instanceof TileEntityDebugReceiver) {
                String normalizedKey = message.linkKey == null ? "" : message.linkKey.trim();
                ((TileEntityDebugReceiver) te).linkKey = normalizedKey;
                te.markDirty();
                world.markBlockForUpdate(message.x, message.y, message.z);
            }
            return null;
        }
    }
}
