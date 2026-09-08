package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import java.util.*;

/** Only requested missing TEs. This data expires with the session; it never creates client TEs. */
public class PacketSpeakerFallback implements IMessage {
    public static final class Target {
        public final long position;
        public final int range;
        public final float volume;
        public Target(long position, int range, float volume) { this.position = position; this.range = range; this.volume = volume; }
    }
    public long sessionId;
    public List<Target> targets = new ArrayList<>();
    public PacketSpeakerFallback() {}
    public PacketSpeakerFallback(long id) { sessionId = id; }
    @Override public void fromBytes(ByteBuf buf) {
        sessionId = buf.readLong();
        int count = PacketLimits.readCount(buf, PacketLimits.SESSION_TARGETS);
        if (count > buf.readableBytes()/16) throw new io.netty.handler.codec.DecoderException("SAM fallback targets");
        targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long position = buf.readLong(); int range = buf.readInt(); float volume = buf.readFloat();
            if (!PacketLimits.speaker(range, volume)) throw new io.netty.handler.codec.DecoderException("SAM fallback settings");
            targets.add(new Target(position, range, volume));
        }
    }
    @Override public void toBytes(ByteBuf buf) {
        PacketLimits.checkCount(targets.size(), PacketLimits.SESSION_TARGETS);
        buf.writeLong(sessionId); buf.writeInt(targets.size());
        for (Target target : targets) { buf.writeLong(target.position); buf.writeInt(target.range); buf.writeFloat(target.volume); }
    }
}
