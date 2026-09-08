package jp.me1han.sam.network;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
/** ID zero is reserved for the explicit administrator stop-all command. */
public class PacketAnnounceStop implements IMessage {
    public long sessionId;
    public PacketAnnounceStop() {}
    public PacketAnnounceStop(long id) { sessionId = id; }
    @Override public void fromBytes(ByteBuf buf) { sessionId = buf.readLong(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeLong(sessionId); }
}
