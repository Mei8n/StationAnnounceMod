package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketDepartureMelodyConfig implements IMessage {
    public int x, y, z;
    public String linkKey;
    public String soundId;
    public String scriptName;

    public PacketDepartureMelodyConfig() {}

    public PacketDepartureMelodyConfig(int x, int y, int z, String linkKey, String soundId, String scriptName) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.linkKey = linkKey;
        this.soundId = soundId;
        this.scriptName = scriptName;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        this.soundId = PacketLimits.readString(buf, PacketLimits.NAME);
        this.scriptName = PacketLimits.readString(buf, PacketLimits.NAME);
        PacketLimits.requireDecoded(isValidPayload(), "Invalid departure melody config payload");
    }

    public boolean isValidPayload() {
        return ConfigAccess.key(PacketLimits.normalize(linkKey))
            && PacketLimits.string(PacketLimits.normalize(soundId), PacketLimits.NAME)
            && PacketLimits.string(PacketLimits.normalize(scriptName), PacketLimits.NAME);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketLimits.require(isValidPayload(), "Invalid departure melody config payload");
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        PacketLimits.writeString(buf, this.linkKey, PacketLimits.LINK_KEY);
        PacketLimits.writeString(buf, this.soundId, PacketLimits.NAME);
        PacketLimits.writeString(buf, this.scriptName, PacketLimits.NAME);
    }
}
