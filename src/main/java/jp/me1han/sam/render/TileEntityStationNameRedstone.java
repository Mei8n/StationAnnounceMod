package jp.me1han.sam.render;

/** Rising-edge redstone station-name trigger. */
public final class TileEntityStationNameRedstone extends TileEntityStationNameAnnouncer {
    public void onRedstoneUpdate(boolean powered) {
        if (worldObj == null || worldObj.isRemote) return;
        if (updateRedstoneEdgeState(powered) && powered) triggerStationName();
    }

    @Override protected boolean usesRedstoneEdgeInput() { return true; }
    @Override public boolean canUpdate() { return false; }
}
