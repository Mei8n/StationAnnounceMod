package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;

public final class PacketLimits {
    /** Maximum UTF-8 payload length representable by Forge's two-byte VarInt string prefix. */
    public static final int MAX_UTF8_WIRE_BYTES = 0x3FFF;
    public static final int LINK_KEY = 64, NAME = 256, MODEL = 128;
    public static final int CONDITIONS = 64, SOUNDS = 256, SOUND_LIST = 8192;
    public static final int SESSION_TARGETS = 512, MISSING_TARGETS = SESSION_TARGETS;
    /** Enough chunks to represent every entry addressable by a Java collection. */
    public static final int ROUTE_CHUNKS = (int)((Integer.MAX_VALUE + (long)SESSION_TARGETS - 1) / SESSION_TARGETS);
    public static final int BODY_SOUNDS = 256;
    public static final int MAX_ANNOUNCE_REPEATS = jp.me1han.sam.api.AnnounceData.MAX_REPEAT_COUNT;
    public static final int MAX_RANGE = 128, MAX_TICKS = 1728000;
    public static final float MAX_VOLUME = 1.0F;
    private PacketLimits() {}
    public static String normalize(String value) { return value == null ? "" : value; }
    public static void checkCount(int count, int max) {
        if (count < 0 || count > max) throw new IllegalArgumentException("SAM packet count exceeds " + max);
    }
    private static int maxUtf8Bytes(int max) {
        if (max < 0) throw new IllegalArgumentException("SAM string limit must not be negative");
        return (int)Math.min((long)max * 3L, (long)MAX_UTF8_WIRE_BYTES);
    }
    public static boolean string(String value, int max) {
        return value != null && value.length() <= max
            && value.getBytes(StandardCharsets.UTF_8).length <= maxUtf8Bytes(max);
    }
    /** Normalize nullable legacy fields, validate exactly as readString(), then encode. */
    public static void writeString(ByteBuf buf, String value, int max) {
        String normalized = normalize(value);
        if (!string(normalized, max)) throw new IllegalArgumentException("SAM string length exceeds " + max);
        ByteBufUtils.writeUTF8String(buf, normalized);
    }
    public static void writeCount(ByteBuf buf, int count, int max) {
        checkCount(count, max);
        buf.writeInt(count);
    }
    /** Check UTF-8 byte length before allocating (Forge uses a two-byte varint). */
    public static String readString(ByteBuf buf, int max) {
        int bytes = ByteBufUtils.readVarInt(buf, 2);
        if (bytes < 0 || bytes > maxUtf8Bytes(max) || bytes > buf.readableBytes()) throw new DecoderException("SAM string length");
        String value = buf.toString(buf.readerIndex(), bytes, StandardCharsets.UTF_8);
        buf.skipBytes(bytes);
        if (!string(value, max)) throw new DecoderException("SAM string length");
        return value;
    }
    public static int readCount(ByteBuf buf, int max) {
        int count = buf.readInt();
        if (count < 0 || count > max) throw new DecoderException("SAM list length");
        return count;
    }
    public static boolean speaker(int range, float volume) {
        return range > 0 && range <= MAX_RANGE && !Float.isNaN(volume) && !Float.isInfinite(volume)
            && volume >= 0 && volume <= MAX_VOLUME;
    }
    public static boolean sounds(String list) {
        if (!string(list, SOUND_LIST)) return false;
        String[] sounds = list.split("[,;\\r\\n]+", -1);
        if (sounds.length > SOUNDS) return false;
        for (String sound : sounds) if (!string(sound.trim(), NAME)) return false;
        return true;
    }
    public static boolean ticks(int value, int minimum) {
        return value >= minimum && value <= MAX_TICKS;
    }
    public static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
    public static boolean slot(int value) { return value >= 0 && value <= 8; }
    public static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
    public static void requireDecoded(boolean valid, String message) {
        if (!valid) throw new DecoderException(message);
    }
}
