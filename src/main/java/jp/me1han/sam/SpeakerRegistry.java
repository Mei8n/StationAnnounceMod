package jp.me1han.sam;

import jp.me1han.sam.network.PacketAnnounce;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SpeakerRegistry {
    private static class SpeakerEntry {
        int dimensionId;
        int x;
        int y;
        int z;
        String linkKey;
        int range;
        float volume;
    }

    private static final List<SpeakerEntry> ENTRIES = new ArrayList<SpeakerEntry>();

    private SpeakerRegistry() {}

    public static synchronized void upsert(int dimensionId, int x, int y, int z, String linkKey, int range, float volume) {
        removeAt(dimensionId, x, y, z);
        String normalized = normalizeKey(linkKey);
        if (normalized.isEmpty()) {
            return;
        }

        SpeakerEntry entry = new SpeakerEntry();
        entry.dimensionId = dimensionId;
        entry.x = x;
        entry.y = y;
        entry.z = z;
        entry.linkKey = normalized;
        entry.range = range;
        entry.volume = volume;
        ENTRIES.add(entry);
    }

    public static synchronized void removeAt(int dimensionId, int x, int y, int z) {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            SpeakerEntry entry = ENTRIES.get(i);
            if (entry.dimensionId == dimensionId && entry.x == x && entry.y == y && entry.z == z) {
                ENTRIES.remove(i);
            }
        }
    }

    public static synchronized List<PacketAnnounce.SpeakerData> findByKey(int dimensionId, String linkKey) {
        String normalized = normalizeKey(linkKey);
        List<PacketAnnounce.SpeakerData> speakers = new ArrayList<PacketAnnounce.SpeakerData>();
        if (normalized.isEmpty()) {
            return speakers;
        }

        for (SpeakerEntry entry : ENTRIES) {
            if (entry.dimensionId == dimensionId && normalized.equals(entry.linkKey)) {
                speakers.add(new PacketAnnounce.SpeakerData(entry.x, entry.y, entry.z, entry.range, entry.volume));
            }
        }

        return speakers;
    }

    public static synchronized int countByDimension(int dimensionId) {
        int count = 0;
        for (SpeakerEntry entry : ENTRIES) {
            if (entry.dimensionId == dimensionId) {
                count++;
            }
        }
        return count;
    }

    public static synchronized String sampleKeys(int dimensionId, int limit) {
        if (limit <= 0) {
            return "";
        }

        Set<String> keys = new LinkedHashSet<String>();
        for (SpeakerEntry entry : ENTRIES) {
            if (entry.dimensionId != dimensionId) {
                continue;
            }
            keys.add(entry.linkKey);
            if (keys.size() >= limit) {
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(key);
        }
        return sb.toString();
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim();
    }
}

