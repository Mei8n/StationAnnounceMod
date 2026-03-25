package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class MessageConfig implements IMessage {
    public int x, y, z;
    public String scriptName;
    public String linkKey;

    public MessageConfig() {}

    public MessageConfig(int x, int y, int z, String scriptName, String linkKey) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.scriptName = scriptName;
        this.linkKey = linkKey;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.scriptName = ByteBufUtils.readUTF8String(buf);
        this.linkKey = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        // ★修正：第1引数に buf を渡す必要があります
        ByteBufUtils.writeUTF8String(buf, this.scriptName);
        ByteBufUtils.writeUTF8String(buf, this.linkKey);
    }

    public static class Handler implements IMessageHandler<MessageConfig, IMessage> {
        @Override
        public IMessage onMessage(MessageConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity tile = world.getTileEntity(message.x, message.y, message.z);

            System.out.println("[SAM-DEBUG] Handler received: key=" + message.linkKey);

            if (tile instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer announcer = (TileEntityAnnouncer) tile;

                announcer.setScriptName(message.scriptName);
                announcer.linkKey = message.linkKey;

                // ★重要：サーバー側のデータを保存
                announcer.markDirty();

                // ★最重要：クライアント側に「データが変わったから再同期して」と通知を送る
                // これがないと、GUIを開いたときに古い（空の）データが表示されてしまいます
                world.markBlockForUpdate(message.x, message.y, message.z);
                jp.me1han.sam.StationAnnounceModCore.logger.info("[SAM-DEBUG] TileEntity updated on Server. Current linkKey in TE: " + announcer.linkKey);
            }
            else {
                // ★TileEntityが見つからない場合の警告
                jp.me1han.sam.StationAnnounceModCore.logger.error("[SAM-DEBUG] Error: TileEntity at pos is NOT an Announcer!");
            }
            return null;
        }
    }
}
