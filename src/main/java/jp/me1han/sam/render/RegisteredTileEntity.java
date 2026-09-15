package jp.me1han.sam.render;

import jp.me1han.sam.LoadedSamTiles;
import jp.me1han.sam.link.SamLinkRegistry;
import net.minecraft.tileentity.TileEntity;

public abstract class RegisteredTileEntity extends TileEntity {
    private boolean redstoneEdgeInitialized;
    private boolean redstoneEdgePowered;

    /** Opt-in for devices whose Redstone input reacts only to a real OFF-to-ON edge. */
    protected boolean usesRedstoneEdgeInput() { return false; }

    @Override public void validate() {
        super.validate();
        LoadedSamTiles.register(this);
        SamLinkRegistry.register(this);
    }

    /**
     * Captures the initial Redstone level after chunk loading has completed.
     * Calling World#getBlock from validate() can synchronously load a neighbouring
     * chunk and recursively validate this tile until the server stack overflows.
     */
    protected final void initializeRedstoneEdgeState() {
        if (redstoneEdgeInitialized || !usesRedstoneEdgeInput()
                || worldObj == null || worldObj.isRemote) return;
        redstoneEdgePowered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        redstoneEdgeInitialized = true;
    }

    /** Updates the cached level and reports whether it changed after a valid baseline existed. */
    protected final boolean updateRedstoneEdgeState(boolean powered) {
        if (!redstoneEdgeInitialized) {
            redstoneEdgePowered = powered;
            redstoneEdgeInitialized = true;
            return false;
        }
        if (redstoneEdgePowered == powered) return false;
        redstoneEdgePowered = powered;
        return true;
    }

    private void clearRedstoneEdgeState() { redstoneEdgeInitialized = false; }

    @Override public void invalidate() {
        clearRedstoneEdgeState();
        SamLinkRegistry.unregister(this); LoadedSamTiles.unregister(this); super.invalidate();
    }
    @Override public void onChunkUnload() {
        clearRedstoneEdgeState();
        SamLinkRegistry.unregister(this); LoadedSamTiles.unregister(this); super.onChunkUnload();
    }
}
