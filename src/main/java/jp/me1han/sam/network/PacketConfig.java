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
        this.scriptName = PacketLimits.readString(buf, PacketLimits.NAME);
        this.linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
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
        @Override public IMessage onMessage(PacketConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, TileEntityAnnouncer.class, tile -> {
                if (!ConfigAccess.key(m.linkKey) || !PacketLimits.string(m.scriptName, PacketLimits.NAME)) return;
                if (!ConfigAccess.normalize(m.linkKey).equals(ConfigAccess.normalize(tile.linkKey))) ServerSessions.stopOwner(tile);
                ConfigAccess.change(tile, () -> {
                    tile.setScriptName(m.scriptName); tile.linkKey = ConfigAccess.normalize(m.linkKey);
                    tile.playLocalSound = m.playLocalSound;
                });
            }); return null;
        }
    }
}
