package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;

/** One bounded chunk of a complete, server-authoritative session routing snapshot. */
public class PacketSessionSpeakerRoutes implements IMessage {
    public static final class Target {
        public final long position;
        public final int range;
        public final float volume;

        public Target(long position, int range, float volume) {
            this.position = position;
            this.range = range;
            this.volume = volume;
        }
    }

    public long sessionId;
    public long revision;
    public int chunkIndex;
    public int chunkCount;
    public List<Target> targets = new ArrayList<>();

    public PacketSessionSpeakerRoutes() {}

    public PacketSessionSpeakerRoutes(long sessionId, long revision, int chunkIndex, int chunkCount) {
        this.sessionId = sessionId;
        this.revision = revision;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
    }

    private void validateHeader() {
        if (sessionId <= 0 || revision <= 0 || chunkCount < 1
            || chunkCount > PacketLimits.ROUTE_CHUNKS || chunkIndex < 0 || chunkIndex >= chunkCount)
            throw new IllegalArgumentException("Invalid SAM route snapshot header");
    }

    @Override public void fromBytes(ByteBuf buf) {
        sessionId = buf.readLong();
        revision = buf.readLong();
        chunkIndex = buf.readInt();
        chunkCount = buf.readInt();
        try { validateHeader(); }
        catch (IllegalArgumentException invalid) { throw new DecoderException(invalid.getMessage(), invalid); }
        int count = PacketLimits.readCount(buf, PacketLimits.SESSION_TARGETS);
        if (buf.readableBytes() != count * 16) throw new DecoderException("SAM route targets");
        targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long position = buf.readLong();
            int range = buf.readInt();
            float volume = buf.readFloat();
            if (!PacketLimits.speaker(range, volume)) throw new DecoderException("SAM route settings");
            targets.add(new Target(position, range, volume));
        }
    }

    @Override public void toBytes(ByteBuf buf) {
        validateHeader();
        PacketLimits.checkCount(targets == null ? -1 : targets.size(), PacketLimits.SESSION_TARGETS);
        buf.writeLong(sessionId);
        buf.writeLong(revision);
        buf.writeInt(chunkIndex);
        buf.writeInt(chunkCount);
        buf.writeInt(targets.size());
        for (Target target : targets) {
            if (target == null || !PacketLimits.speaker(target.range, target.volume))
                throw new IllegalArgumentException("Invalid SAM route settings");
            buf.writeLong(target.position);
            buf.writeInt(target.range);
            buf.writeFloat(target.volume);
        }
    }
}
