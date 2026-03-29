package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketDebugAnnounceEvent implements IMessage {
    public String eventType; // "START" or "STOP"
    public String linkKey;
    public String soundId;
    public int matchedSpeakers;
    public boolean playLocalSound;

    public PacketDebugAnnounceEvent() {}

    public PacketDebugAnnounceEvent(String eventType, String linkKey, String soundId, int matchedSpeakers, boolean playLocalSound) {
        this.eventType = eventType;
        this.linkKey = linkKey;
        this.soundId = soundId;
        this.matchedSpeakers = matchedSpeakers;
        this.playLocalSound = playLocalSound;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.eventType = ByteBufUtils.readUTF8String(buf);
        this.linkKey = ByteBufUtils.readUTF8String(buf);
        this.soundId = ByteBufUtils.readUTF8String(buf);
        this.matchedSpeakers = buf.readInt();
        this.playLocalSound = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.eventType != null ? this.eventType : "");
        ByteBufUtils.writeUTF8String(buf, this.linkKey != null ? this.linkKey : "");
        ByteBufUtils.writeUTF8String(buf, this.soundId != null ? this.soundId : "");
        buf.writeInt(this.matchedSpeakers);
        buf.writeBoolean(this.playLocalSound);
    }
}

