package jp.me1han.sam.script;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jp.me1han.sam.render.TileEntityAnnouncer;

/** Read-only, point-in-time view exposed to an ordinary announcement script. */
public final class AnnounceScriptContext extends ScriptTileContext {
    private final Map<String, String> receivedData;

    private AnnounceScriptContext(String linkKey, Map<String, String> receivedData) {
        super(linkKey);
        Map<String, String> snapshot = receivedData == null
            ? new HashMap<String, String>() : new HashMap<String, String>(receivedData);
        this.receivedData = Collections.unmodifiableMap(snapshot);
    }

    public static AnnounceScriptContext snapshot(TileEntityAnnouncer tile) {
        return tile == null ? new AnnounceScriptContext("", null)
            : new AnnounceScriptContext(tile.getLinkKey(), tile.receivedData);
    }

    public Map<String, String> getReceivedData() { return receivedData; }
}
