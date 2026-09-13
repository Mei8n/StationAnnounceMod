package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.api.DepartureSequence;

/** Server-authoritative logical position for a player attaching to an active session. */
public class PacketSessionTimeline implements IMessage {
    public long sessionId;
    public long elapsedTicks;
    /** Departure release position, or -1 while a toggle remains ON. */
    public long releaseElapsedTicks = -1;
    public boolean hasDepartureSnapshot;
    public boolean departureOn;
    public boolean departureMelodyPlaying;
    public int departureMelodyRemaining;
    public int departureTailPhase;
    public int departureClosingIndex;
    public int departureClosingRemaining;

    public PacketSessionTimeline() {}
    public PacketSessionTimeline(long sessionId, long elapsedTicks, long releaseElapsedTicks) {
        this.sessionId = sessionId;
        this.elapsedTicks = elapsedTicks;
        this.releaseElapsedTicks = releaseElapsedTicks;
    }

    public PacketSessionTimeline(long sessionId, long elapsedTicks, DepartureSequence.Snapshot snapshot) {
        this(sessionId, elapsedTicks, -1);
        if (snapshot == null) return;
        hasDepartureSnapshot = true;
        departureOn = snapshot.on;
        departureMelodyPlaying = snapshot.melodyPlaying;
        departureMelodyRemaining = snapshot.melodyRemaining;
        departureTailPhase = snapshot.tailPhase.id;
        departureClosingIndex = snapshot.closingIndex;
        departureClosingRemaining = snapshot.closingRemaining;
    }

    public DepartureSequence.Snapshot departureSnapshot() {
        if (!hasDepartureSnapshot) return null;
        return new DepartureSequence.Snapshot(departureOn, departureMelodyPlaying,
            departureMelodyRemaining, DepartureSequence.TailPhase.fromId(departureTailPhase),
            departureClosingIndex, departureClosingRemaining);
    }

    private void validate() {
        PacketLimits.require(sessionId > 0, "Invalid session timeline ID");
        PacketLimits.require(elapsedTicks >= 0 && elapsedTicks <= ServerSessions.SESSION_TTL_TICKS * 2,
            "Invalid session elapsed time");
        PacketLimits.require(releaseElapsedTicks >= -1 && releaseElapsedTicks <= elapsedTicks,
            "Invalid session release time");
        PacketLimits.require(!hasDepartureSnapshot
            || (departureMelodyRemaining >= 0 && departureMelodyRemaining <= 72000
                && departureTailPhase >= 0 && departureTailPhase <= DepartureSequence.TailPhase.DONE.id
                && departureClosingIndex >= 0 && departureClosingIndex <= PacketLimits.BODY_SOUNDS
                && departureClosingRemaining >= 0 && departureClosingRemaining <= 72000),
            "Invalid departure timeline snapshot");
    }

    @Override public void fromBytes(ByteBuf buf) {
        sessionId = buf.readLong();
        elapsedTicks = buf.readLong();
        releaseElapsedTicks = buf.readLong();
        hasDepartureSnapshot = buf.readBoolean();
        if (hasDepartureSnapshot) {
            departureOn = buf.readBoolean();
            departureMelodyPlaying = buf.readBoolean();
            departureMelodyRemaining = buf.readInt();
            departureTailPhase = buf.readUnsignedByte();
            departureClosingIndex = buf.readInt();
            departureClosingRemaining = buf.readInt();
        }
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
        buf.writeBoolean(hasDepartureSnapshot);
        if (hasDepartureSnapshot) {
            buf.writeBoolean(departureOn);
            buf.writeBoolean(departureMelodyPlaying);
            buf.writeInt(departureMelodyRemaining);
            buf.writeByte(departureTailPhase);
            buf.writeInt(departureClosingIndex);
            buf.writeInt(departureClosingRemaining);
        }
    }
}
