package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Exceptional request: once per recipient/session, only targets not resolved from client TEs. */
public class PacketMissingSpeakers implements IMessage {
    public long sessionId;
    public long[] targets = new long[0];
    public PacketMissingSpeakers() {}
    public PacketMissingSpeakers(long id, long[] targets) { sessionId = id; this.targets = targets; }
    @Override public void fromBytes(ByteBuf buf) {
        sessionId = buf.readLong();
        int count = PacketLimits.readCount(buf, 65536);
        if (count > buf.readableBytes() / 8) throw new io.netty.handler.codec.DecoderException("SAM missing targets");
        targets = new long[count];
        for (int i = 0; i < count; i++) targets[i] = buf.readLong();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(sessionId); buf.writeInt(targets.length);
        for (long target : targets) buf.writeLong(target);
    }
}
