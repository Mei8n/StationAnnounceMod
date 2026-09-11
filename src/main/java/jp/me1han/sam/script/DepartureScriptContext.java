package jp.me1han.sam.script;

import jp.me1han.sam.render.TileEntityDepartureMelody;

/** Read-only context for departure scripts; receivedData is intentionally absent. */
public final class DepartureScriptContext extends ScriptTileContext {
    private DepartureScriptContext(String linkKey) { super(linkKey); }

    public static DepartureScriptContext snapshot(TileEntityDepartureMelody tile) {
        return new DepartureScriptContext(tile == null ? "" : tile.getLinkKey());
    }
}
