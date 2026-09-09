package jp.me1han.sam.link;

/** Canonical meaning of a logical SAM device link key. */
public final class LinkKey {
    private LinkKey() {}

    public static String normalize(String key) {
        return key == null ? "" : key.trim();
    }

    public static boolean isEmpty(String key) {
        return normalize(key).isEmpty();
    }

    public static boolean equals(String a, String b) {
        return normalize(a).equals(normalize(b));
    }
}
