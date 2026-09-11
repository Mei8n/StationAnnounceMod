package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketStartAnnouncerConfig implements IMessage {
    public int x, y, z;
    public String linkKey;
    public boolean isControlCar;

    public PacketStartAnnouncerConfig() {}

    public PacketStartAnnouncerConfig(int x, int y, int z, String linkKey, boolean isControlCar) {
        this.x = x; this.y = y; this.z = z; this.linkKey = linkKey; this.isControlCar = isControlCar;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        this.isControlCar = buf.readBoolean();
        PacketLimits.requireDecoded(isValidPayload(), "Invalid start announcer config payload");
    }

    public boolean isValidPayload() { return ConfigAccess.key(PacketLimits.normalize(linkKey)); }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketLimits.require(isValidPayload(), "Invalid start announcer config payload");
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        PacketLimits.writeString(buf, this.linkKey, PacketLimits.LINK_KEY);
        buf.writeBoolean(isControlCar);
    }
}
