package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.api.ScriptType;
import jp.me1han.sam.render.TileEntityStationNameAnnouncer;

/** One bounded, server-validated config packet shared by both station-name blocks. */
public final class PacketStationNameConfig implements IMessage {
    public int x, y, z;
    public String linkKey = "";
    public String scriptName = "";

    public PacketStationNameConfig() {}
    public PacketStationNameConfig(int x, int y, int z, String linkKey, String scriptName) {
        this.x=x; this.y=y; this.z=z; this.linkKey=linkKey; this.scriptName=scriptName;
    }
    @Override public void fromBytes(ByteBuf buf) {
        x=buf.readInt(); y=buf.readInt(); z=buf.readInt();
        linkKey=PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        scriptName=PacketLimits.readString(buf, PacketLimits.NAME);
        PacketLimits.requireDecoded(isValidPayload(), "Invalid station-name config payload");
    }
    @Override public void toBytes(ByteBuf buf) {
        PacketLimits.require(isValidPayload(), "Invalid station-name config payload");
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        PacketLimits.writeString(buf, linkKey, PacketLimits.LINK_KEY);
        PacketLimits.writeString(buf, scriptName, PacketLimits.NAME);
    }
    public boolean isValidPayload() {
        return ConfigAccess.key(PacketLimits.normalize(linkKey))
            && PacketLimits.string(PacketLimits.normalize(scriptName), PacketLimits.NAME);
    }
    public static final class Handler implements IMessageHandler<PacketStationNameConfig, IMessage> {
        @Override public IMessage onMessage(PacketStationNameConfig message, MessageContext context) {
            ConfigAccess.enqueue(context, message.x, message.y, message.z,
                TileEntityStationNameAnnouncer.class, tile -> {
                    if (!message.isValidPayload()) return;
                    String script = PacketLimits.normalize(message.scriptName);
                    if (!AnnouncePackLoader.validateScript(script, ScriptType.STATION_NAME)) return;
                    if (!tile.applyConfig(message.linkKey, script)) return;
                    tile.markDirty();
                    tile.getWorldObj().markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
                });
            return null;
        }
    }
}
