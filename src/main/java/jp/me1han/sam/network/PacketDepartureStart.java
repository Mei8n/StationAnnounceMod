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
        departure.doorClose = PacketLimits.readString(buf, PacketLimits.NAME);
        departure.melodyTicks = buf.readInt(); departure.doorCloseTicks = buf.readInt(); departure.intervalTicks = buf.readInt();
    }
    @Override public void toBytes(ByteBuf buf) {
        writeHeader(buf);
        buf.writeBoolean(departure.alternate); buf.writeBoolean(departure.finishChorus);
        ByteBufUtils.writeUTF8String(buf, departure.melody); ByteBufUtils.writeUTF8String(buf, departure.doorClose);
        buf.writeInt(departure.melodyTicks); buf.writeInt(departure.doorCloseTicks); buf.writeInt(departure.intervalTicks);
    }
}
