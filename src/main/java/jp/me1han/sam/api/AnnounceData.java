package jp.me1han.sam.api;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class AnnounceData {
    public static final int MAX_REPEAT_COUNT = 100;
    public final String startMelo;
    public final List<String> bodySounds;
    /** Zero for a sound, otherwise the duration of a silent body part. */
    public final List<Integer> bodyIntervalTicks;
    public final String arrMelo;
    public final int repeatCount;

    public AnnounceData(String start, List<String> body, String loop) {
        this(start, body, loop, 1);
    }

    public AnnounceData(String start, List<String> body, String loop, int repeatCount) {
        this(start, body, Collections.<Integer>emptyList(), loop, repeatCount);
    }

    public AnnounceData(String start, List<String> body, List<Integer> intervals, String loop, int repeatCount) {
        this.startMelo = start;
        this.bodySounds = body;
        this.bodyIntervalTicks = new ArrayList<>();
        for (int i = 0; i < body.size(); i++)
            this.bodyIntervalTicks.add(intervals != null && i < intervals.size() ? intervals.get(i) : 0);
        this.arrMelo = loop;
        this.repeatCount = normalizeRepeatCount(repeatCount);
    }

    public static int normalizeRepeatCount(int value) {
        return Math.max(1, Math.min(MAX_REPEAT_COUNT, value));
    }
}
