package jp.me1han.sam.network;

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
        this.linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        this.range = buf.readInt();
        this.volume = buf.readFloat();
        PacketLimits.requireDecoded(isValidPayload(), "Invalid speaker config payload");
    }

    public boolean isValidPayload() {
        return ConfigAccess.key(PacketLimits.normalize(linkKey)) && PacketLimits.speaker(range, volume);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketLimits.require(isValidPayload(), "Invalid speaker config payload");
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        PacketLimits.writeString(buf, this.linkKey, PacketLimits.LINK_KEY);
        buf.writeInt(range);
        buf.writeFloat(volume);
    }
}
