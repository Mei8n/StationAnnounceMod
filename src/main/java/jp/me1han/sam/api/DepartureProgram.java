package jp.me1han.sam.api;

import java.util.Map;

/** Script-defined controls; audio durations are resolved exclusively from sam_length.json. */
public final class DepartureProgram {
    public boolean alternate;
    public boolean finishChorus;
    public String melody = "";
    public final java.util.List<String> doorCloseSounds = new java.util.ArrayList<>();
    public final java.util.List<Integer> doorCloseIntervalTicks = new java.util.ArrayList<>();
    public final java.util.List<Integer> doorCloseDurations = new java.util.ArrayList<>();
    public int melodyTicks;
    public int doorCloseTicks;
    public int intervalTicks;

    public DepartureProgram(boolean alternate) { this.alternate = alternate; }
    public DepartureProgram interval(double seconds) {
        ticks(seconds, true); // Validate before truncating, including negative fractions.
        java.math.BigDecimal hundredths = java.math.BigDecimal.valueOf(seconds)
            .setScale(2, java.math.RoundingMode.DOWN);
        intervalTicks = hundredths.multiply(java.math.BigDecimal.valueOf(20))
            .setScale(0, java.math.RoundingMode.CEILING).intValueExact();
        return this;
    }
    public DepartureProgram tachikawa(boolean enabled) { finishChorus = enabled; return this; }

    public DepartureProgram resolve(Map<String, Integer> lengths) {
        DepartureProgram copy = new DepartureProgram(alternate);
        copy.finishChorus = finishChorus;
        copy.melody = clean(melody);
        if (copy.melody.isEmpty()) throw new IllegalArgumentException("A departure melody is required");
        copy.melodyTicks = duration(copy.melody, lengths);
        copy.doorCloseSounds.addAll(doorCloseSounds);
        copy.doorCloseIntervalTicks.addAll(doorCloseIntervalTicks);
        if (copy.doorCloseSounds.size() > 256) throw new IllegalArgumentException("Too many door-close sounds (maximum 256)");
        for (int i = 0; i < copy.doorCloseSounds.size(); i++) {
            String sound = copy.doorCloseSounds.get(i);
            int interval = i < doorCloseIntervalTicks.size() ? doorCloseIntervalTicks.get(i) : 0;
            if (sound.isEmpty() && interval == 0 && i < doorCloseDurations.size()) interval = doorCloseDurations.get(i);
            int length = interval > 0 ? interval : duration(sound, lengths);
            if (interval > 72000) throw new IllegalArgumentException("Invalid door-close interval");
            copy.doorCloseDurations.add(length);
            copy.doorCloseTicks += length;
        }
        if (intervalTicks < 0 || intervalTicks > 72000) throw new IllegalArgumentException("Invalid interval");
        copy.intervalTicks = intervalTicks;
        return copy;
    }

    private static int duration(String id, Map<String, Integer> lengths) {
        Integer value = lengths.get(id);
        if (value == null || value < 1 || value > 72000) {
            throw new IllegalArgumentException("Missing or invalid duration in sam_length.json (>0 to 3600 seconds): " + id);
        }
        return value;
    }

    private static int ticks(double seconds, boolean zeroAllowed) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0
            || (!zeroAllowed && seconds == 0) || seconds > 3600) {
            throw new IllegalArgumentException("Duration must be " + (zeroAllowed ? "0" : ">0") + " to 3600 seconds");
        }
        return (int) Math.ceil(seconds * 20.0);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
