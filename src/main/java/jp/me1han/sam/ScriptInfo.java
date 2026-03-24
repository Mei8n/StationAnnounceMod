package jp.me1han.sam;

import java.io.Serializable;

public class ScriptInfo implements Serializable {
    public final String fileName;
    public final String displayName;

    public ScriptInfo(String fileName, String displayName) {
        this.fileName = fileName;
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
