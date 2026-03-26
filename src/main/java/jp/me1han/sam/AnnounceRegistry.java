package jp.me1han.sam;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnnounceRegistry {
    public static final Map<String, Integer> soundDurationMap = new ConcurrentHashMap<>();
    public static final Map<String, List<AnnouncePart>> scriptCache = new ConcurrentHashMap<>();

    public static class AnnouncePart {
        public final String soundId;
        public final int durationTicks;

        public AnnouncePart(String id, int ticks) {
            this.soundId = id;
            this.durationTicks = ticks;
        }
    }
}
