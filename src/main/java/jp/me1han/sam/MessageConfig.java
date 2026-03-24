package jp.me1han.sam;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class MessageConfig implements IMessage {
    public int x, y, z;
    public String scriptName;

    public MessageConfig() {}

    public MessageConfig(int x, int y, int z, String scriptName) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.scriptName = scriptName;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.scriptName = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        ByteBufUtils.writeUTF8String(buf, this.scriptName);
    }
}
