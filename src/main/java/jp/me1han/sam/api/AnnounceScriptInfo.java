package jp.me1han.sam.api;

import java.io.Serializable;

public class AnnounceScriptInfo implements Serializable {
    public final String fileName;
    public final String displayName;
    public final ScriptType scriptType;

    public AnnounceScriptInfo(String fileName, String displayName) {
        this(fileName, displayName, ScriptType.UNKNOWN);
    }

    public AnnounceScriptInfo(String fileName, String displayName, ScriptType scriptType) {
        this.fileName = fileName;
        this.displayName = displayName;
        this.scriptType = scriptType == null ? ScriptType.UNKNOWN : scriptType;
    }

    public boolean isCompatibleWith(ScriptType required) { return scriptType.isCompatibleWith(required); }
    public ScriptType getScriptType() { return scriptType; }

    @Override
    public String toString() {
        return displayName;
    }
}
