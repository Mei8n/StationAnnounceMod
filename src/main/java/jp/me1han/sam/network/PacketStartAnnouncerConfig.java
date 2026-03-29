package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
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
        this.linkKey = ByteBufUtils.readUTF8String(buf);
        this.isControlCar = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        ByteBufUtils.writeUTF8String(buf, linkKey);
        buf.writeBoolean(isControlCar);
    }
}
