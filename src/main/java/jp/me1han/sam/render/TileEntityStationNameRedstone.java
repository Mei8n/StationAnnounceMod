package jp.me1han.sam.render;

/** Rising-edge redstone station-name trigger. */
public final class TileEntityStationNameRedstone extends TileEntityStationNameAnnouncer {
    private boolean lastPowered;

    public void onRedstoneUpdate(boolean powered) {
        if (worldObj == null || worldObj.isRemote) return;
        if (powered && !lastPowered) triggerStationName();
        lastPowered = powered;
    }

    @Override public boolean canUpdate() { return false; }
}
