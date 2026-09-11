package jp.me1han.sam.network;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
/** ID zero is reserved for the explicit administrator stop-all command. */
public class PacketAnnounceStop implements IMessage {
    public long sessionId;
    public PacketAnnounceStop() {}
    public PacketAnnounceStop(long id) { sessionId = id; }
    private void validatePayload() { PacketLimits.require(sessionId >= 0, "Invalid stop session ID"); }
    @Override public void fromBytes(ByteBuf buf) {
        sessionId = buf.readLong(); PacketLimits.requireDecoded(sessionId >= 0, "Invalid stop session ID");
    }
    @Override public void toBytes(ByteBuf buf) { validatePayload(); buf.writeLong(sessionId); }
}
