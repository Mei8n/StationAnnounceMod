package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketSpeakerConfig implements IMessage {
    public int x, y, z;
    public String linkKey;
    public int range;
    public float volume;

    public PacketSpeakerConfig() {}

    public PacketSpeakerConfig(int x, int y, int z, String linkKey, int range, float volume) {
        this.x = x; this.y = y; this.z = z;
        this.linkKey = linkKey;
        this.range = range;
        this.volume = volume;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.linkKey = ByteBufUtils.readUTF8String(buf);
        this.range = buf.readInt();
        this.volume = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        ByteBufUtils.writeUTF8String(buf, this.linkKey != null ? this.linkKey : "");
        buf.writeInt(range);
        buf.writeFloat(volume);
    }
}
