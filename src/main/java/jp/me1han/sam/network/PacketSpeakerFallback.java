package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import java.util.*;

/** Legacy bounded wire packet retained for discriminator compatibility. */
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
        PacketLimits.requireDecoded(isValidPayload(), "Invalid SAM fallback payload");
    }
    public boolean isValidPayload() {
        if (sessionId <= 0 || targets == null || targets.size() > PacketLimits.SESSION_TARGETS) return false;
        for (Target target : targets)
            if (target == null || !PacketLimits.speaker(target.range, target.volume)) return false;
        return true;
    }
    @Override public void toBytes(ByteBuf buf) {
        PacketLimits.require(isValidPayload(), "Invalid SAM fallback payload");
        buf.writeLong(sessionId); PacketLimits.writeCount(buf, targets.size(), PacketLimits.SESSION_TARGETS);
        for (Target target : targets) { buf.writeLong(target.position); buf.writeInt(target.range); buf.writeFloat(target.volume); }
    }
}
