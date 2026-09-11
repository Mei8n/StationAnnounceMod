package jp.me1han.sam.network;

import io.netty.buffer.ByteBuf;
import jp.me1han.sam.api.DepartureProgram;

/** Departure START: common session header followed only by the resolved program. */
public class PacketDepartureStart extends PacketAnnounce {
    public DepartureProgram departure;
    public PacketDepartureStart() { priority = PRIORITY_DEPARTURE_MELODY; }
    @Override public void fromBytes(ByteBuf buf) {
        readHeader(buf);
        departure = new DepartureProgram(buf.readBoolean());
        departure.finishChorus = buf.readBoolean();
        departure.melody = PacketLimits.readString(buf, PacketLimits.NAME);
        departure.melodyTicks = buf.readInt(); departure.doorCloseTicks = buf.readInt(); departure.intervalTicks = buf.readInt();
        int count = PacketLimits.readCount(buf, PacketLimits.BODY_SOUNDS);
        for (int i = 0; i < count; i++) {
            departure.doorCloseSounds.add(PacketLimits.readString(buf, PacketLimits.NAME));
            int ticks = buf.readInt();
            departure.doorCloseDurations.add(ticks);
        }
        try { validateDeparturePayload(); }
        catch (IllegalArgumentException invalid) {
            throw new io.netty.handler.codec.DecoderException(invalid.getMessage(), invalid);
        }
    }
    public void validateDeparturePayload() {
        validateHeaderPayload();
        PacketLimits.require(priority == PRIORITY_DEPARTURE_MELODY && departure != null,
            "Invalid departure payload");
        PacketLimits.require(PacketLimits.string(PacketLimits.normalize(departure.melody), PacketLimits.NAME)
            && !PacketLimits.normalize(departure.melody).trim().isEmpty(), "Invalid departure melody");
        PacketLimits.require(departure.melodyTicks >= 1 && departure.melodyTicks <= MAX_DURATION_TICKS,
            "Invalid departure melody duration");
        PacketLimits.require(departure.intervalTicks >= 0 && departure.intervalTicks <= MAX_DURATION_TICKS,
            "Invalid departure interval");
        PacketLimits.require(departure.doorCloseSounds != null && departure.doorCloseDurations != null
            && departure.doorCloseSounds.size() == departure.doorCloseDurations.size(),
            "Door-close parts and durations must match");
        PacketLimits.checkCount(departure.doorCloseSounds.size(), PacketLimits.BODY_SOUNDS);
        long total = 0;
        for (int i = 0; i < departure.doorCloseSounds.size(); i++) {
            String sound = departure.doorCloseSounds.get(i);
            Integer ticks = departure.doorCloseDurations.get(i);
            PacketLimits.require(PacketLimits.string(PacketLimits.normalize(sound), PacketLimits.NAME),
                "Invalid door-close sound");
            PacketLimits.require(ticks != null && ticks >= 1 && ticks <= MAX_DURATION_TICKS,
                "Invalid door-close duration");
            total += ticks;
        }
        PacketLimits.require(total == departure.doorCloseTicks && total <= Integer.MAX_VALUE,
            "Invalid total door-close duration");
    }
    @Override public void toBytes(ByteBuf buf) {
        validateDeparturePayload();
        writeHeader(buf);
        buf.writeBoolean(departure.alternate); buf.writeBoolean(departure.finishChorus);
        PacketLimits.writeString(buf, departure.melody, PacketLimits.NAME);
        buf.writeInt(departure.melodyTicks); buf.writeInt(departure.doorCloseTicks); buf.writeInt(departure.intervalTicks);
        PacketLimits.writeCount(buf, departure.doorCloseSounds.size(), PacketLimits.BODY_SOUNDS);
        for (int i = 0; i < departure.doorCloseSounds.size(); i++) {
            PacketLimits.writeString(buf, departure.doorCloseSounds.get(i), PacketLimits.NAME);
            buf.writeInt(departure.doorCloseDurations.get(i));
        }
    }
}
