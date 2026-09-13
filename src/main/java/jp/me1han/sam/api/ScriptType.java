package jp.me1han.sam.api;

/** Stable, append-only identifiers for the SAM execution path a script supports. */
public enum ScriptType {
    UNKNOWN(-1),
    APPROACH(0),
    ARRIVAL(1),
    STATION_NAME(2),
    DEPARTURE_MELODY(3),
    AWARENESS(4);

    public final int id;

    ScriptType(int id) { this.id = id; }

    public int getId() { return id; }

    public static ScriptType fromId(int id) {
        for (ScriptType type : values()) if (type.id == id) return type;
        return UNKNOWN;
    }

    public static boolean isDeclaredId(int id) {
        for (ScriptType type : values()) if (type.id == id) return true;
        return false;
    }

    public boolean isCompatibleWith(ScriptType required) {
        return this == UNKNOWN || this == required;
    }
}
