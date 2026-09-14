package jp.me1han.sam.render;

import jp.me1han.sam.compat.TrainCompatRegistry;
import jp.me1han.sam.compat.TrainDetectionManager;
import jp.me1han.sam.compat.TrainSnapshot;
import net.minecraft.util.AxisAlignedBB;

/** ATSAssist GroundUnit-style exact-stop detector using SAM's shared train index. */
public final class TileEntityStationNameStopDetector extends TileEntityStationNameAnnouncer {
    private long lastFormationId = -1L;
    private boolean wasStopped;

    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || !TrainCompatRegistry.get().isAvailable()) return;
        TrainSnapshot train = TrainDetectionManager.findFirstTrain(worldObj, detectionBounds(), true);
        if (train == null) { resetDetection(); return; }
        long formationId = train.getFormationId();
        if (formationId != lastFormationId) {
            lastFormationId = formationId;
            wasStopped = false;
        }
        float speed = train.getSpeed();
        boolean stopped = !Float.isNaN(speed) && !Float.isInfinite(speed) && speed == 0F;
        if (stopped && !wasStopped) triggerStationName();
        // Latch the event even if script/parent/session validation rejected playback.
        wasStopped = stopped;
    }

    public AxisAlignedBB detectionBounds() {
        return AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 3, zCoord + 1);
    }
    private void resetDetection() { lastFormationId = -1L; wasStopped = false; }
    public long getLastFormationId() { return lastFormationId; }
    public boolean wasStopped() { return wasStopped; }
}
