package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;

public final class PacketLimits {
    public static final int LINK_KEY = 64, NAME = 256, MODEL = 128;
    public static final int CONDITIONS = 64, SOUNDS = 256, SOUND_LIST = 8192;
    public static final int SESSION_TARGETS = 512, MISSING_TARGETS = SESSION_TARGETS;
    public static final int BODY_SOUNDS = 256;
    public static final int MAX_RANGE = 128, MAX_TICKS = 1728000;
    public static final float MAX_VOLUME = 1.0F;
    private PacketLimits() {}
    public static void checkCount(int count, int max) {
        if (count < 0 || count > max) throw new IllegalArgumentException("SAM packet count exceeds " + max);
    }
    public static boolean string(String value, int max) { return value != null && value.length() <= max; }
    /** Check UTF-8 byte length before allocating (Forge uses a two-byte varint). */
    public static String readString(ByteBuf buf, int max) {
        int bytes = ByteBufUtils.readVarInt(buf, 2);
        if (bytes < 0 || bytes > max * 3 || bytes > buf.readableBytes()) throw new DecoderException("SAM string length");
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
}
