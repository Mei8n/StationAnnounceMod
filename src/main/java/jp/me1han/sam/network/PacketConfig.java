package jp.me1han.sam.network;

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
        PacketLimits.requireDecoded(isValidPayload(), "Invalid announcer config payload");
    }

    public boolean isValidPayload() {
        return PacketLimits.string(PacketLimits.normalize(scriptName), PacketLimits.NAME)
            && ConfigAccess.key(PacketLimits.normalize(linkKey));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketLimits.require(isValidPayload(), "Invalid announcer config payload");
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        PacketLimits.writeString(buf, this.scriptName, PacketLimits.NAME);
        PacketLimits.writeString(buf, this.linkKey, PacketLimits.LINK_KEY);
        buf.writeBoolean(this.playLocalSound);
    }

    public static class Handler implements IMessageHandler<PacketConfig, IMessage> {
        @Override public IMessage onMessage(PacketConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, TileEntityAnnouncer.class, tile -> {
                if (!m.isValidPayload()) return;
                if (!ConfigAccess.normalize(m.linkKey).equals(tile.getLinkKey())) ServerSessions.stopOwner(tile);
                ConfigAccess.change(tile, () -> {
                    tile.setScriptName(m.scriptName); tile.setLinkKey(m.linkKey);
                    tile.playLocalSound = m.playLocalSound;
                });
            }); return null;
        }
    }
}
