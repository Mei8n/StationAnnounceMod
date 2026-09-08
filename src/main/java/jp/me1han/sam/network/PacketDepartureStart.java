package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
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
            if (ticks < 1 || ticks > 72000) throw new IllegalArgumentException("Invalid door-close duration");
            departure.doorCloseDurations.add(ticks);
        }
    }
    @Override public void toBytes(ByteBuf buf) {
        if (departure.doorCloseDurations.size() != departure.doorCloseSounds.size())
            throw new IllegalArgumentException("Door-close parts and durations must match");
        writeHeader(buf);
        buf.writeBoolean(departure.alternate); buf.writeBoolean(departure.finishChorus);
        ByteBufUtils.writeUTF8String(buf, departure.melody);
        buf.writeInt(departure.melodyTicks); buf.writeInt(departure.doorCloseTicks); buf.writeInt(departure.intervalTicks);
        PacketLimits.checkCount(departure.doorCloseSounds.size(), PacketLimits.BODY_SOUNDS);
        buf.writeInt(departure.doorCloseSounds.size());
        for (int i = 0; i < departure.doorCloseSounds.size(); i++) {
            ByteBufUtils.writeUTF8String(buf, departure.doorCloseSounds.get(i));
            buf.writeInt(departure.doorCloseDurations.get(i));
        }
    }
}
