package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.api.AwarenessMode;

public class PacketAwarenessConfig implements IMessage {
    public int x, y, z;
    public String linkKey;
    public String soundList;
    public int mode;
    public String scriptName;
    public int intervalTicks;
    public boolean randomOrder;
    public boolean allowOverlap;
    public boolean playAfterDeparture;
    public int departureDelayTicks;
    public boolean requireRedstone;

    public PacketAwarenessConfig() {}

    public PacketAwarenessConfig(int x, int y, int z, String linkKey, String soundList, int intervalTicks,
                                 boolean randomOrder, boolean allowOverlap, boolean playAfterDeparture,
                                 int departureDelayTicks) {
        this(x, y, z, linkKey, soundList, AwarenessMode.DIRECT, "", intervalTicks, randomOrder,
            allowOverlap, playAfterDeparture, departureDelayTicks, false);
    }

    public PacketAwarenessConfig(int x, int y, int z, String linkKey, String soundList,
                                 AwarenessMode mode, String scriptName, int intervalTicks,
                                 boolean randomOrder, boolean allowOverlap, boolean playAfterDeparture,
                                 int departureDelayTicks) {
        this(x, y, z, linkKey, soundList, mode, scriptName, intervalTicks, randomOrder, allowOverlap,
            playAfterDeparture, departureDelayTicks, false);
    }

    public PacketAwarenessConfig(int x, int y, int z, String linkKey, String soundList,
                                 AwarenessMode mode, String scriptName, int intervalTicks,
                                 boolean randomOrder, boolean allowOverlap, boolean playAfterDeparture,
                                 int departureDelayTicks, boolean requireRedstone) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.linkKey = linkKey;
        this.soundList = soundList;
        this.mode = mode == null ? -1 : mode.id;
        this.scriptName = scriptName;
        this.intervalTicks = intervalTicks;
        this.randomOrder = randomOrder;
        this.allowOverlap = allowOverlap;
        this.playAfterDeparture = playAfterDeparture;
        this.departureDelayTicks = departureDelayTicks;
        this.requireRedstone = requireRedstone;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        this.soundList = PacketLimits.readString(buf, PacketLimits.SOUND_LIST);
        this.mode = buf.readInt();
        this.scriptName = PacketLimits.readString(buf, PacketLimits.NAME);
        this.intervalTicks = buf.readInt();
        this.randomOrder = buf.readBoolean();
        this.allowOverlap = buf.readBoolean();
        this.playAfterDeparture = buf.readBoolean();
        this.departureDelayTicks = buf.readInt();
        this.requireRedstone = buf.readBoolean();
        PacketLimits.requireDecoded(isValidPayload(), "Invalid Awareness config payload");
    }

    public boolean isValidPayload() {
        return ConfigAccess.key(PacketLimits.normalize(linkKey))
            && PacketLimits.sounds(PacketLimits.normalize(soundList))
            && AwarenessMode.isValidId(mode)
            && PacketLimits.string(PacketLimits.normalize(scriptName), PacketLimits.NAME)
            && PacketLimits.ticks(intervalTicks, 20)
            && PacketLimits.ticks(departureDelayTicks, 0);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketLimits.require(isValidPayload(), "Invalid Awareness config payload");
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        PacketLimits.writeString(buf, this.linkKey, PacketLimits.LINK_KEY);
        PacketLimits.writeString(buf, this.soundList, PacketLimits.SOUND_LIST);
        buf.writeInt(this.mode);
        PacketLimits.writeString(buf, this.scriptName, PacketLimits.NAME);
        buf.writeInt(this.intervalTicks);
        buf.writeBoolean(this.randomOrder);
        buf.writeBoolean(this.allowOverlap);
        buf.writeBoolean(this.playAfterDeparture);
        buf.writeInt(this.departureDelayTicks);
        buf.writeBoolean(this.requireRedstone);
    }
}
