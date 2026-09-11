package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.api.AnnounceData;
import java.util.*;

/** Ordinary/awareness START only. Speaker settings belong to TE description packets. */
public class PacketAnnounce implements IMessage {
    static final int TIMING_MAGIC = 0x53414D54; // "SAMT"
    public static final int MAX_DURATION_TICKS = 72000;
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
    /** Server-authoritative durations. bodyPartTicks has the same indexes as bodySounds. */
    public int startMeloTicks, arrMeloTicks;
    public List<Integer> bodyPartTicks = new ArrayList<>();
    public int repeatCount = 1;

    public PacketAnnounce() {}
    public PacketAnnounce(AnnounceData data, String key, boolean local, int x, int y, int z) {
        linkKey = jp.me1han.sam.link.LinkKey.normalize(key); playLocalSound = local;
        this.x = x; this.y = y; this.z = z;
        startMelo = data.startMelo; bodySounds = data.bodySounds;
        bodyIntervalTicks = data.bodyIntervalTicks; arrMelo = data.arrMelo;
        repeatCount = data.repeatCount;
    }

    /** Resolve ordinary/awareness timing once, on the logical server, before START delivery. */
    public void resolveTiming(Map<String, Integer> lengths) {
        if (bodySounds == null) bodySounds = new ArrayList<>();
        else bodySounds = new ArrayList<>(bodySounds);
        if (bodySounds.size() > PacketLimits.BODY_SOUNDS)
            throw new IllegalArgumentException("Too many announcement body sounds (maximum " + PacketLimits.BODY_SOUNDS + ")");
        if (bodyIntervalTicks != null && !bodyIntervalTicks.isEmpty()
            && bodyIntervalTicks.size() != bodySounds.size())
            throw new IllegalArgumentException("Announcement body interval count must match body sounds");
        if (repeatCount < 1 || repeatCount > PacketLimits.MAX_ANNOUNCE_REPEATS)
            throw new IllegalArgumentException("Invalid announcement repeat count");
        startMelo = clean(startMelo);
        arrMelo = clean(arrMelo);
        startMeloTicks = startMelo.isEmpty() ? 0 : duration(startMelo, lengths);
        arrMeloTicks = arrMelo.isEmpty() ? 0 : duration(arrMelo, lengths);
        List<Integer> resolved = new ArrayList<>(bodySounds.size());
        List<Integer> intervals = new ArrayList<>(bodySounds.size());
        for (int i = 0; i < bodySounds.size(); i++) {
            String sound = clean(bodySounds.get(i));
            bodySounds.set(i, sound);
            Integer intervalValue = bodyIntervalTicks != null && i < bodyIntervalTicks.size()
                ? bodyIntervalTicks.get(i) : Integer.valueOf(0);
            if (intervalValue == null)
                throw new IllegalArgumentException("Invalid announcement body interval at index " + i);
            int interval = intervalValue;
            if (interval < 0 || interval > MAX_DURATION_TICKS
                || (sound.isEmpty() ? interval < 1 : interval != 0))
                throw new IllegalArgumentException("Invalid announcement body interval at index " + i);
            intervals.add(interval);
            resolved.add(interval > 0 ? interval : duration(sound, lengths));
        }
        bodyIntervalTicks = intervals;
        bodyPartTicks = resolved;
        validateTiming();
    }

    /** Validate canonical timing without consulting any local duration table. */
    public void validateTiming() {
        int bodySize = bodySounds == null ? 0 : bodySounds.size();
        if (bodySize > PacketLimits.BODY_SOUNDS || bodyPartTicks == null || bodyPartTicks.size() != bodySize)
            throw new IllegalArgumentException("Announcement body sounds and durations must match");
        validateOptionalSound(startMelo, startMeloTicks, "start melody");
        validateOptionalSound(arrMelo, arrMeloTicks, "arrival melody");
        PacketLimits.require(PacketLimits.string(PacketLimits.normalize(startMelo), PacketLimits.NAME)
            && PacketLimits.string(PacketLimits.normalize(arrMelo), PacketLimits.NAME),
            "Invalid announcement melody ID");
        if (repeatCount < 1 || repeatCount > PacketLimits.MAX_ANNOUNCE_REPEATS)
            throw new IllegalArgumentException("Invalid announcement repeat count");
        for (int i = 0; i < bodySize; i++) {
            String sound = clean(bodySounds.get(i));
            PacketLimits.require(PacketLimits.string(PacketLimits.normalize(bodySounds.get(i)), PacketLimits.NAME),
                "Invalid announcement body sound at index " + i);
            Integer tickValue = bodyPartTicks.get(i);
            if (tickValue == null)
                throw new IllegalArgumentException("Invalid announcement body duration at index " + i);
            int ticks = tickValue;
            if (ticks < 1 || ticks > MAX_DURATION_TICKS)
                throw new IllegalArgumentException("Invalid announcement body duration at index " + i);
            Integer intervalValue = bodyIntervalTicks != null && i < bodyIntervalTicks.size()
                ? bodyIntervalTicks.get(i) : Integer.valueOf(0);
            if (intervalValue == null)
                throw new IllegalArgumentException("Invalid announcement body interval at index " + i);
            int interval = intervalValue;
            if (sound.isEmpty()) {
                if (interval != ticks) throw new IllegalArgumentException("Announcement interval duration mismatch at index " + i);
            } else if (interval != 0) {
                throw new IllegalArgumentException("Sound entry cannot also be an interval at index " + i);
            }
        }
    }

    private static void validateOptionalSound(String sound, int ticks, String label) {
        boolean empty = clean(sound).isEmpty();
        if ((empty && ticks != 0) || (!empty && (ticks < 1 || ticks > MAX_DURATION_TICKS)))
            throw new IllegalArgumentException("Invalid announcement " + label + " duration");
    }

    private static int duration(String id, Map<String, Integer> lengths) {
        Integer value = lengths == null ? null : lengths.get(id);
        if (value == null || value < 1 || value > MAX_DURATION_TICKS)
            throw new IllegalArgumentException("Missing or invalid duration in sam_length.json: " + id);
        return value;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    protected void validateHeaderPayload() {
        PacketLimits.require(sessionId > 0, "Invalid announcement session ID");
        PacketLimits.require(PacketLimits.string(PacketLimits.normalize(linkKey), PacketLimits.LINK_KEY),
            "Invalid announcement link key");
        PacketLimits.checkCount(targets == null ? -1 : targets.length, PacketLimits.SESSION_TARGETS);
        PacketLimits.require(priority == PRIORITY_AWARENESS || priority == PRIORITY_ANNOUNCE
            || priority == PRIORITY_DEPARTURE_MELODY, "Invalid announcement priority");
    }
    public void validatePayload() {
        validateHeaderPayload();
        validateTiming();
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
        validateHeaderPayload();
        buf.writeLong(sessionId); PacketLimits.writeString(buf, linkKey, PacketLimits.LINK_KEY);
        buf.writeInt(priority); buf.writeBoolean(allowOverlap); buf.writeBoolean(playLocalSound);
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        PacketLimits.writeCount(buf, targets.length, PacketLimits.SESSION_TARGETS);
        for (long target : targets) buf.writeLong(target);
    }
    @Override public void fromBytes(ByteBuf buf) {
        readHeader(buf);
        startMelo = PacketLimits.readString(buf, PacketLimits.NAME);
        arrMelo = PacketLimits.readString(buf, PacketLimits.NAME);
        int size = PacketLimits.readCount(buf, PacketLimits.BODY_SOUNDS);
        bodySounds = new ArrayList<>();
        for (int i = 0; i < size; i++) bodySounds.add(PacketLimits.readString(buf, PacketLimits.NAME));
        if (buf.readableBytes() < 20 || buf.readInt() != TIMING_MAGIC)
            throw new io.netty.handler.codec.DecoderException("SAM canonical announcement timing is missing");
        repeatCount = buf.readInt();
        startMeloTicks = buf.readInt();
        arrMeloTicks = buf.readInt();
        int durations = PacketLimits.readCount(buf, PacketLimits.BODY_SOUNDS);
        if (durations != size || buf.readableBytes() != durations * 4)
            throw new io.netty.handler.codec.DecoderException("SAM body durations");
        bodyPartTicks = new ArrayList<>(durations);
        bodyIntervalTicks = new ArrayList<>(durations);
        for (int i = 0; i < durations; i++) {
            int ticks = buf.readInt();
            bodyPartTicks.add(ticks);
            bodyIntervalTicks.add(bodySounds.get(i).isEmpty() ? ticks : 0);
        }
        try { validatePayload(); }
        catch (IllegalArgumentException invalid) {
            throw new io.netty.handler.codec.DecoderException(invalid.getMessage(), invalid);
        }
    }
    @Override public void toBytes(ByteBuf buf) {
        validatePayload();
        writeHeader(buf);
        PacketLimits.writeString(buf, startMelo, PacketLimits.NAME);
        PacketLimits.writeString(buf, arrMelo, PacketLimits.NAME);
        PacketLimits.writeCount(buf, bodySounds.size(), PacketLimits.BODY_SOUNDS);
        for (String sound : bodySounds) PacketLimits.writeString(buf, sound, PacketLimits.NAME);
        buf.writeInt(TIMING_MAGIC);
        buf.writeInt(repeatCount);
        buf.writeInt(startMeloTicks);
        buf.writeInt(arrMeloTicks);
        PacketLimits.writeCount(buf, bodyPartTicks.size(), PacketLimits.BODY_SOUNDS);
        for (int ticks : bodyPartTicks) buf.writeInt(ticks);
    }
}
