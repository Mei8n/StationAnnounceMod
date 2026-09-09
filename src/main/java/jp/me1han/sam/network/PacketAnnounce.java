package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.api.AnnounceData;
import java.util.*;

/** Ordinary/awareness START only. Speaker settings belong to TE description packets. */
public class PacketAnnounce implements IMessage {
    public static final int PRIORITY_AWARENESS = 0, PRIORITY_ANNOUNCE = 10, PRIORITY_DEPARTURE_MELODY = 20;
    public long sessionId;
    public String linkKey = "";
    public int priority = PRIORITY_ANNOUNCE;
    public boolean allowOverlap, playLocalSound;
    public int x, y, z;
    public long[] targets = new long[0];
    public String startMelo = "", arrMelo = "";
    public List<String> bodySounds = new ArrayList<>();
    public List<Integer> bodyIntervalTicks = new ArrayList<>();
    public int repeatCount = 1;

    public PacketAnnounce() {}
    public PacketAnnounce(AnnounceData data, String key, boolean local, int x, int y, int z) {
        linkKey = key == null ? "" : key.trim(); playLocalSound = local;
        this.x = x; this.y = y; this.z = z;
        startMelo = data.startMelo; bodySounds = data.bodySounds;
        bodyIntervalTicks = data.bodyIntervalTicks; arrMelo = data.arrMelo;
        repeatCount = data.repeatCount;
    }
    protected void readHeader(ByteBuf buf) {
        sessionId = buf.readLong(); linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        priority = buf.readInt(); allowOverlap = buf.readBoolean(); playLocalSound = buf.readBoolean();
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt();
        int size = PacketLimits.readCount(buf, PacketLimits.SESSION_TARGETS);
        if (size > buf.readableBytes() / 8) throw new io.netty.handler.codec.DecoderException("SAM targets");
        targets = new long[size];
        for (int i = 0; i < size; i++) targets[i] = buf.readLong();
    }
    protected void writeHeader(ByteBuf buf) {
        PacketLimits.checkCount(targets.length, PacketLimits.SESSION_TARGETS);
        buf.writeLong(sessionId); ByteBufUtils.writeUTF8String(buf, linkKey);
        buf.writeInt(priority); buf.writeBoolean(allowOverlap); buf.writeBoolean(playLocalSound);
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        buf.writeInt(targets.length);
        for (long target : targets) buf.writeLong(target);
    }
    @Override public void fromBytes(ByteBuf buf) {
        readHeader(buf);
        startMelo = PacketLimits.readString(buf, PacketLimits.NAME);
        arrMelo = PacketLimits.readString(buf, PacketLimits.NAME);
        int size = PacketLimits.readCount(buf, PacketLimits.BODY_SOUNDS);
        bodySounds = new ArrayList<>();
        for (int i = 0; i < size; i++) bodySounds.add(PacketLimits.readString(buf, PacketLimits.NAME));
        bodyIntervalTicks = new ArrayList<>(Collections.nCopies(size, 0));
        // Keep the legacy payload prefix readable: packets without the appended field mean one play.
        repeatCount = 1;
        if (buf.isReadable()) {
            if (buf.readableBytes() < 4) throw new io.netty.handler.codec.DecoderException("SAM repeat count");
            repeatCount = buf.readInt();
            if (repeatCount < 1 || repeatCount > PacketLimits.MAX_ANNOUNCE_REPEATS)
                throw new io.netty.handler.codec.DecoderException("SAM repeat count");
            if (buf.isReadable()) {
                int intervals = PacketLimits.readCount(buf, PacketLimits.BODY_SOUNDS);
                if (intervals != size || buf.readableBytes() != intervals * 4)
                    throw new io.netty.handler.codec.DecoderException("SAM body intervals");
                bodyIntervalTicks.clear();
                for (int i = 0; i < intervals; i++) {
                    int ticks = buf.readInt();
                    if (ticks < 0 || ticks > 72000 || (ticks > 0) == !bodySounds.get(i).isEmpty())
                        throw new io.netty.handler.codec.DecoderException("SAM body interval");
                    bodyIntervalTicks.add(ticks);
                }
            }
        }
    }
    @Override public void toBytes(ByteBuf buf) {
        PacketLimits.checkCount(bodySounds == null ? 0 : bodySounds.size(), PacketLimits.BODY_SOUNDS);
        if (repeatCount < 1 || repeatCount > PacketLimits.MAX_ANNOUNCE_REPEATS)
            throw new IllegalArgumentException("SAM repeat count must be 1 to " + PacketLimits.MAX_ANNOUNCE_REPEATS);
        boolean hasIntervals = false;
        if (bodyIntervalTicks != null && !bodyIntervalTicks.isEmpty()) {
            for (int ticks : bodyIntervalTicks) {
                if (ticks < 0 || ticks > 72000) throw new IllegalArgumentException("Invalid SAM body interval");
                hasIntervals |= ticks > 0;
            }
            if (hasIntervals) {
                if (bodyIntervalTicks.size() != bodySounds.size())
                    throw new IllegalArgumentException("SAM body interval count must match body sounds");
                for (int i = 0; i < bodyIntervalTicks.size(); i++)
                    if ((bodyIntervalTicks.get(i) > 0) == !bodySounds.get(i).isEmpty())
                        throw new IllegalArgumentException("Invalid SAM body interval");
            }
        }
        writeHeader(buf);
        ByteBufUtils.writeUTF8String(buf, startMelo == null ? "" : startMelo);
        ByteBufUtils.writeUTF8String(buf, arrMelo == null ? "" : arrMelo);
        buf.writeInt(bodySounds == null ? 0 : bodySounds.size());
        if (bodySounds != null) for (String sound : bodySounds) ByteBufUtils.writeUTF8String(buf, sound == null ? "" : sound);
        // Keep repeat-one packets without intervals byte-compatible with the original format.
        if (repeatCount != 1 || hasIntervals) buf.writeInt(repeatCount);
        if (hasIntervals) {
            buf.writeInt(bodyIntervalTicks.size());
            for (int ticks : bodyIntervalTicks) buf.writeInt(ticks);
        }
    }
}
