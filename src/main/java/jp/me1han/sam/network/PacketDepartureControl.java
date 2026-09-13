package jp.me1han.sam.network;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
/** Event-driven control of exactly one departure session. */
public class PacketDepartureControl implements IMessage {
    public enum Action {
        RELEASE(0), REENGAGE(1), CANCEL(2);
        public final int id;
        Action(int id) { this.id = id; }
        static Action fromId(int id) {
            for (Action action : values()) if (action.id == id) return action;
            throw new IllegalArgumentException("Invalid departure control action");
        }
    }
    public long sessionId;
    public Action action;
    public PacketDepartureControl() {}
    public PacketDepartureControl(long id, Action action) { sessionId = id; this.action = action; }
    @Override public void fromBytes(ByteBuf buf) {
        sessionId = buf.readLong();
        try { action = Action.fromId(buf.readUnsignedByte()); }
        catch (IllegalArgumentException invalid) {
            throw new io.netty.handler.codec.DecoderException(invalid.getMessage(), invalid);
        }
        PacketLimits.requireDecoded(sessionId > 0, "Invalid departure session ID");
    }
    @Override public void toBytes(ByteBuf buf) {
        PacketLimits.require(sessionId > 0 && action != null, "Invalid departure control");
        buf.writeLong(sessionId); buf.writeByte(action.id);
    }
}
