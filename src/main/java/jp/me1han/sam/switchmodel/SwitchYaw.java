package jp.me1han.sam.switchmodel;

/** Shared angle rules for placement, configuration and model bounds. */
public final class SwitchYaw {
    private SwitchYaw() {}

    public static float normalize(double yaw) {
        if (Double.isNaN(yaw) || Double.isInfinite(yaw)) throw new IllegalArgumentException("Non-finite yaw");
        double wrapped = yaw % 360.0D;
        if (wrapped < 0) wrapped += 360.0D;
        float result = (float) wrapped;
        return result >= 360 || result == 0 ? 0 : result;
    }

    public static float placement(float playerYaw, boolean sneaking) {
        double interval = sneaking ? 1.0D : 15.0D;
        // RTM machine models use -Z as their front, so face that side toward the placer.
        double yaw = normalize(- (double) playerYaw + 180.0D);
        return normalize(Math.floor((yaw + interval / 2) / interval) * interval);
    }

    public static int parse(String text) {
        return (int) normalize(Integer.parseInt(text));
    }

    /** Axis-aligned envelope of all four corners, with the same pivot/sign as OpenGL. */
    public static double[] rotateBounds(double[] bounds, float yaw) {
        double[] result = bounds.clone();
        result[0] = result[2] = Double.POSITIVE_INFINITY;
        result[3] = result[5] = Double.NEGATIVE_INFINITY;
        double radians = Math.toRadians(yaw), cos = Math.cos(radians), sin = Math.sin(radians);
        for (int i : new int[]{0, 3}) for (int j : new int[]{2, 5}) {
            double x = bounds[i] - 0.5, z = bounds[j] - 0.5;
            double rx = 0.5 + x * cos + z * sin, rz = 0.5 - x * sin + z * cos;
            result[0] = Math.min(result[0], rx); result[3] = Math.max(result[3], rx);
            result[2] = Math.min(result[2], rz); result[5] = Math.max(result[5], rz);
        }
        return result;
    }
}
