package jp.me1han.sam;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class MessageAnnouncerUpdate implements IMessage {
    private int x, y, z;
    private String scriptName;
    private String linkKey;

    public MessageAnnouncerUpdate() {}

    public MessageAnnouncerUpdate(int x, int y, int z, String scriptName, String linkKey) {
        this.x = x; this.y = y; this.z = z;
        this.scriptName = scriptName;
        this.linkKey = linkKey;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        scriptName = ByteBufUtils.readUTF8String(buf);
        linkKey = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        ByteBufUtils.writeUTF8String(buf, scriptName);
        ByteBufUtils.writeUTF8String(buf, linkKey);
    }

    public static class Handler implements IMessageHandler<MessageAnnouncerUpdate, IMessage> {
        @Override
        public IMessage onMessage(MessageAnnouncerUpdate msg, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
            if (te instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer announcer = (TileEntityAnnouncer) te;
                announcer.setScriptName(msg.scriptName);
                announcer.setLinkKey(msg.linkKey);
                // サーバー側でデータを保存し、チャンクを保存対象にする
                announcer.markDirty();
                // 周囲のクライアントにTileEntityの更新を通知（必要に応じて）
                world.markBlockForUpdate(msg.x, msg.y, msg.z);
            }
            return null;
        }
    }
}
