package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class PacketConfig implements IMessage {
    public int x, y, z;
    public String scriptName;
    public String linkKey;

    public PacketConfig() {}

    public PacketConfig(int x, int y, int z, String scriptName, String linkKey) {
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
        ByteBufUtils.writeUTF8String(buf, this.scriptName);
        ByteBufUtils.writeUTF8String(buf, this.linkKey);
    }

    public static class Handler implements IMessageHandler<PacketConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity tile = world.getTileEntity(message.x, message.y, message.z);

            System.out.println("[SAM-DEBUG] Handler received: key=" + message.linkKey);

            if (tile instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer announcer = (TileEntityAnnouncer) tile;

                announcer.setScriptName(message.scriptName);
                announcer.linkKey = message.linkKey;

                announcer.markDirty();

                world.markBlockForUpdate(message.x, message.y, message.z);
                jp.me1han.sam.StationAnnounceModCore.logger.info("[SAM-DEBUG] TileEntity updated on Server. Current linkKey in TE: " + announcer.linkKey);
            }
            else {
                jp.me1han.sam.StationAnnounceModCore.logger.error("[SAM-DEBUG] Error: TileEntity at pos is NOT an Announcer!");
            }
            return null;
        }
    }
}
