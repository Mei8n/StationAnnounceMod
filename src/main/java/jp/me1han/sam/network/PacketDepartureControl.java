package jp.me1han.sam.network;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
/** RELEASE/OFF or CANCEL of exactly one departure session. */
public class PacketDepartureControl implements IMessage {
    public long sessionId;
    public boolean cancel;
    public PacketDepartureControl() {}
    public PacketDepartureControl(long id, boolean cancel) { sessionId = id; this.cancel = cancel; }
    @Override public void fromBytes(ByteBuf buf) { sessionId = buf.readLong(); cancel = buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeLong(sessionId); buf.writeBoolean(cancel); }
}
