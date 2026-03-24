package jp.me1han.sam.api;

import java.io.Serializable;

public class AnnounceScriptInfo implements Serializable {
    public final String fileName;
    public final String displayName;

    public AnnounceScriptInfo(String fileName, String displayName) {
        this.fileName = fileName;
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
