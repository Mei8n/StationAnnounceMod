package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
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
        this.linkKey = ByteBufUtils.readUTF8String(buf);
        this.soundId = ByteBufUtils.readUTF8String(buf);
        this.scriptName = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        ByteBufUtils.writeUTF8String(buf, this.linkKey == null ? "" : this.linkKey);
        ByteBufUtils.writeUTF8String(buf, this.soundId == null ? "" : this.soundId);
        ByteBufUtils.writeUTF8String(buf, this.scriptName == null ? "" : this.scriptName);
    }
}
