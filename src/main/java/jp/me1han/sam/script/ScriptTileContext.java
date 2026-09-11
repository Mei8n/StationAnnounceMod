package jp.me1han.sam.script;

/** Minimal immutable context shared by every supported SAM script kind. */
public class ScriptTileContext {
    private final String linkKey;

    protected ScriptTileContext(String linkKey) {
        this.linkKey = linkKey == null ? "" : linkKey;
    }

    public final String getLinkKey() { return linkKey; }
}
