package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Server-authoritative logical position for a player attaching to an active session. */
public class PacketSessionTimeline implements IMessage {
    public long sessionId;
    public long elapsedTicks;
    /** Departure release position, or -1 while a toggle remains ON. */
    public long releaseElapsedTicks = -1;

    public PacketSessionTimeline() {}
    public PacketSessionTimeline(long sessionId, long elapsedTicks, long releaseElapsedTicks) {
        this.sessionId = sessionId;
        this.elapsedTicks = elapsedTicks;
        this.releaseElapsedTicks = releaseElapsedTicks;
    }

    private void validate() {
        PacketLimits.require(sessionId > 0, "Invalid session timeline ID");
        PacketLimits.require(elapsedTicks >= 0 && elapsedTicks <= ServerSessions.SESSION_TTL_TICKS * 2,
            "Invalid session elapsed time");
        PacketLimits.require(releaseElapsedTicks >= -1 && releaseElapsedTicks <= elapsedTicks,
            "Invalid session release time");
    }

    @Override public void fromBytes(ByteBuf buf) {
        sessionId = buf.readLong();
        elapsedTicks = buf.readLong();
        releaseElapsedTicks = buf.readLong();
        try { validate(); }
        catch (IllegalArgumentException invalid) {
            throw new io.netty.handler.codec.DecoderException(invalid.getMessage(), invalid);
        }
    }

    @Override public void toBytes(ByteBuf buf) {
        validate();
        buf.writeLong(sessionId);
        buf.writeLong(elapsedTicks);
        buf.writeLong(releaseElapsedTicks);
    }
}
