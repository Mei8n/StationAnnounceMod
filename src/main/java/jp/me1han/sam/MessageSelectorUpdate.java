package jp.me1han.sam;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class MessageSelectorUpdate implements IMessage {
    private int x, y, z, type;
    private String link, data;

    public MessageSelectorUpdate() {}

    public MessageSelectorUpdate(int x, int y, int z, String link, String data, int type) {
        this.x = x; this.y = y; this.z = z;
        this.link = link; this.data = data; this.type = type;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt();
        link = ByteBufUtils.readUTF8String(buf);
        data = ByteBufUtils.readUTF8String(buf);
        type = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        ByteBufUtils.writeUTF8String(buf, link);
        ByteBufUtils.writeUTF8String(buf, data);
        buf.writeInt(type);
    }

    public static class Handler implements IMessageHandler<MessageSelectorUpdate, IMessage> {
        @Override
        public IMessage onMessage(MessageSelectorUpdate msg, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
            if (te instanceof TileEntityTrainSelector) {
                TileEntityTrainSelector ts = (TileEntityTrainSelector) te;
                ts.setLinkKey(msg.link);
                ts.setDataKey(msg.data);
                ts.setDataType(msg.type);
                te.markDirty();
            }
            return null;
        }
    }
}
