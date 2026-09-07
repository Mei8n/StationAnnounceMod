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
        linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        ByteBufUtils.writeUTF8String(buf, this.linkKey != null ? this.linkKey : "");
    }

    public static class Handler implements IMessageHandler<PacketDebugConfig, IMessage> {
        @Override public IMessage onMessage(PacketDebugConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, TileEntityDebugReceiver.class, tile -> {
                if (!ConfigAccess.key(m.linkKey)) return;
                ConfigAccess.change(tile, () -> tile.linkKey = ConfigAccess.normalize(m.linkKey));
            }); return null;
        }
    }
}
