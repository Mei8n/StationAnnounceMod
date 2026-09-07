package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Only affects the departure channel, leaving other announcement priorities alone. */
public class PacketDepartureControl implements IMessage {
    public String linkKey;
    public boolean cancel;
    public PacketDepartureControl() {}
    public PacketDepartureControl(String key, boolean cancel) { this.linkKey = key; this.cancel = cancel; }
    @Override public void fromBytes(ByteBuf buf) { linkKey = ByteBufUtils.readUTF8String(buf); cancel = buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeUTF8String(buf, linkKey); buf.writeBoolean(cancel); }
}
