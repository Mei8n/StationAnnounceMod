package jp.me1han.sam.network;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
/** One acknowledgement per session, including priority rejection; never per sound. */
public class PacketSessionFinished implements IMessage {
    public long sessionId;
    public PacketSessionFinished() {}
    public PacketSessionFinished(long id) { sessionId = id; }
    @Override public void fromBytes(ByteBuf buf) {
        sessionId = buf.readLong(); PacketLimits.requireDecoded(sessionId > 0, "Invalid finished session ID");
    }
    @Override public void toBytes(ByteBuf buf) {
        PacketLimits.require(sessionId > 0, "Invalid finished session ID"); buf.writeLong(sessionId);
    }
}
