package jp.me1han.sam.api;

/** A silent item inserted between door-close announcement parts by JavaScript. */
public final class DepartureInterval {
    public final int ticks;

    public DepartureInterval(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds <= 0 || seconds > 3600) {
            throw new IllegalArgumentException("Part interval must be >0 to 3600 seconds");
        }
        java.math.BigDecimal hundredths = java.math.BigDecimal.valueOf(seconds)
            .setScale(2, java.math.RoundingMode.DOWN);
        ticks = hundredths.multiply(java.math.BigDecimal.valueOf(20))
            .setScale(0, java.math.RoundingMode.CEILING).intValueExact();
        if (ticks == 0) throw new IllegalArgumentException("Part interval must be at least 0.01 seconds");
    }
}
