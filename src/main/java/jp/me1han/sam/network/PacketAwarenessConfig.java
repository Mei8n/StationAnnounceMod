package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketAwarenessConfig implements IMessage {
    public int x, y, z;
    public String linkKey;
    public String soundList;
    public int intervalTicks;
    public boolean randomOrder;
    public boolean allowOverlap;
    public boolean playAfterDeparture;
    public int departureDelayTicks;

    public PacketAwarenessConfig() {}

    public PacketAwarenessConfig(int x, int y, int z, String linkKey, String soundList, int intervalTicks,
                                 boolean randomOrder, boolean allowOverlap, boolean playAfterDeparture,
                                 int departureDelayTicks) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.linkKey = linkKey;
        this.soundList = soundList;
        this.intervalTicks = intervalTicks;
        this.randomOrder = randomOrder;
        this.allowOverlap = allowOverlap;
        this.playAfterDeparture = playAfterDeparture;
        this.departureDelayTicks = departureDelayTicks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        this.soundList = PacketLimits.readString(buf, PacketLimits.SOUND_LIST);
        this.intervalTicks = buf.readInt();
        this.randomOrder = buf.readBoolean();
        this.allowOverlap = buf.readBoolean();
        this.playAfterDeparture = buf.readBoolean();
        this.departureDelayTicks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        ByteBufUtils.writeUTF8String(buf, this.linkKey == null ? "" : this.linkKey);
        ByteBufUtils.writeUTF8String(buf, this.soundList == null ? "" : this.soundList);
        buf.writeInt(this.intervalTicks);
        buf.writeBoolean(this.randomOrder);
        buf.writeBoolean(this.allowOverlap);
        buf.writeBoolean(this.playAfterDeparture);
        buf.writeInt(this.departureDelayTicks);
    }
}
