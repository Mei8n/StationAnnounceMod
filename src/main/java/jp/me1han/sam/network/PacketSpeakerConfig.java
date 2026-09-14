package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketSpeakerConfig implements IMessage {
    public int x, y, z;
    public String linkKey;
    public int range;
    public float volume;
    public String modelName;
    public int rotationYaw;
    public float offsetX, offsetY, offsetZ;

    public PacketSpeakerConfig() {}

    public PacketSpeakerConfig(int x, int y, int z, String linkKey, int range, float volume) {
        this(x, y, z, linkKey, range, volume, "", 0, 0, 0, 0);
    }

    public PacketSpeakerConfig(int x, int y, int z, String linkKey, int range, float volume,
            String modelName, int rotationYaw, float offsetX, float offsetY, float offsetZ) {
        this.x = x; this.y = y; this.z = z;
        this.linkKey = linkKey;
        this.range = range;
        this.volume = volume;
        this.modelName = modelName;
        this.rotationYaw = rotationYaw;
        this.offsetX = offsetX; this.offsetY = offsetY; this.offsetZ = offsetZ;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        this.range = buf.readInt();
        this.volume = buf.readFloat();
        this.modelName = PacketLimits.readString(buf, PacketLimits.MODEL);
        this.rotationYaw = buf.readInt();
        this.offsetX = buf.readFloat(); this.offsetY = buf.readFloat(); this.offsetZ = buf.readFloat();
        PacketLimits.requireDecoded(isValidPayload(), "Invalid speaker config payload");
    }

    public boolean isValidPayload() {
        return ConfigAccess.key(PacketLimits.normalize(linkKey)) && PacketLimits.speaker(range, volume)
            && PacketLimits.string(PacketLimits.normalize(modelName), PacketLimits.MODEL)
            && jp.me1han.sam.render.TileEntitySpeaker.validOffset(offsetX)
            && jp.me1han.sam.render.TileEntitySpeaker.validOffset(offsetY)
            && jp.me1han.sam.render.TileEntitySpeaker.validOffset(offsetZ);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketLimits.require(isValidPayload(), "Invalid speaker config payload");
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        PacketLimits.writeString(buf, this.linkKey, PacketLimits.LINK_KEY);
        buf.writeInt(range);
        buf.writeFloat(volume);
        PacketLimits.writeString(buf, modelName, PacketLimits.MODEL);
        buf.writeInt(rotationYaw);
        buf.writeFloat(offsetX); buf.writeFloat(offsetY); buf.writeFloat(offsetZ);
    }
}
