package jp.me1han.sam.api;

import java.util.List;

public class AnnounceData {
    public static final int MAX_REPEAT_COUNT = 100;
    public final String startMelo;
    public final List<String> bodySounds;
    public final String arrMelo;
    public final int repeatCount;

    public AnnounceData(String start, List<String> body, String loop) {
        this(start, body, loop, 1);
    }

    public AnnounceData(String start, List<String> body, String loop, int repeatCount) {
        this.startMelo = start;
        this.bodySounds = body;
        this.arrMelo = loop;
        this.repeatCount = normalizeRepeatCount(repeatCount);
    }

    public static int normalizeRepeatCount(int value) {
        return Math.max(1, Math.min(MAX_REPEAT_COUNT, value));
    }
}
