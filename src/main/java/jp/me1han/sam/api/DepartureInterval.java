package jp.me1han.sam.api;

/** A silent item inserted between announcement parts by JavaScript. */
public final class DepartureInterval {
    public final int ticks;

    public DepartureInterval(double seconds) {
        ticks = secondsToTicks(seconds, false, "Part interval");
    }

    static int secondsToTicks(double seconds, boolean allowZero, String label) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0 || (!allowZero && seconds == 0) || seconds > 3600)
            throw new IllegalArgumentException(label + " must be " + (allowZero ? ">=0" : ">0") + " to 3600 seconds");
        java.math.BigDecimal hundredths = java.math.BigDecimal.valueOf(seconds)
            .setScale(2, java.math.RoundingMode.DOWN);
        int result = hundredths.multiply(java.math.BigDecimal.valueOf(20))
            .setScale(0, java.math.RoundingMode.CEILING).intValueExact();
        if (!allowZero && result == 0) throw new IllegalArgumentException(label + " must be at least 0.01 seconds");
        return result;
    }
}
