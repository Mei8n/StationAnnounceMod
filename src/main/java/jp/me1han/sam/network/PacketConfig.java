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
    public boolean playLocalSound;

    public PacketConfig() {}

    public PacketConfig(int x, int y, int z, String scriptName, String linkKey, boolean playLocalSound) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.scriptName = scriptName;
        this.linkKey = linkKey;
        this.playLocalSound = playLocalSound;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.scriptName = ByteBufUtils.readUTF8String(buf);
        this.linkKey = ByteBufUtils.readUTF8String(buf);
        this.playLocalSound = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        ByteBufUtils.writeUTF8String(buf, this.scriptName != null ? this.scriptName : "");
        ByteBufUtils.writeUTF8String(buf, this.linkKey != null ? this.linkKey : "");
        buf.writeBoolean(this.playLocalSound);
    }

    public static class Handler implements IMessageHandler<PacketConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity tile = world.getTileEntity(message.x, message.y, message.z);

            jp.me1han.sam.StationAnnounceModCore.logger.info("[SAM-DEBUG] Announcer Config Received! script=" + message.scriptName + ", linkKey=" + message.linkKey);

            if (tile instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer announcer = (TileEntityAnnouncer) tile;
                announcer.setScriptName(message.scriptName);
                announcer.linkKey = message.linkKey;
                announcer.playLocalSound = message.playLocalSound;

                announcer.markDirty();
                world.markBlockForUpdate(message.x, message.y, message.z);
            } else {
                jp.me1han.sam.StationAnnounceModCore.logger.warn("[SAM-DEBUG] Error: TileEntity is not Announcer at " + message.x + "," + message.y + "," + message.z);
            }
            return null;
        }
    }
}
