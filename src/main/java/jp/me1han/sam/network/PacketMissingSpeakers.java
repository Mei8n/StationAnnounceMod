package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Legacy wire packet. Dynamic sessions do not accept client-selected coordinates. */
public class PacketMissingSpeakers implements IMessage {
    public long sessionId;
    public long[] targets = new long[0];
    public PacketMissingSpeakers() {}
    public PacketMissingSpeakers(long id, long[] targets) { sessionId = id; this.targets = targets; }
    @Override public void fromBytes(ByteBuf buf) {
        sessionId = buf.readLong();
        int count = PacketLimits.readCount(buf, PacketLimits.MISSING_TARGETS);
        if (count > buf.readableBytes() / 8) throw new io.netty.handler.codec.DecoderException("SAM missing targets");
        targets = new long[count];
        for (int i = 0; i < count; i++) targets[i] = buf.readLong();
        PacketLimits.requireDecoded(sessionId > 0, "Invalid missing Speaker session ID");
    }
    @Override public void toBytes(ByteBuf buf) {
        PacketLimits.require(sessionId > 0 && targets != null, "Invalid missing Speaker payload");
        buf.writeLong(sessionId); PacketLimits.writeCount(buf, targets.length, PacketLimits.MISSING_TARGETS);
        for (long target : targets) buf.writeLong(target);
    }
}
