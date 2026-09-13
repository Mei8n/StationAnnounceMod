package jp.me1han.sam.api;

/** Persisted and network-stable operating modes for the Awareness Announcer. */
public enum AwarenessMode {
    DIRECT(0),
    SCRIPT(1);

    public final int id;

    AwarenessMode(int id) { this.id = id; }

    public static AwarenessMode fromId(int id) {
        for (AwarenessMode mode : values()) if (mode.id == id) return mode;
        return null;
    }

    public static boolean isValidId(int id) { return fromId(id) != null; }
}
