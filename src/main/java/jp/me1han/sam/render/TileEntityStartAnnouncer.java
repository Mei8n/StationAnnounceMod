package jp.me1han.sam.render;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import java.util.List;
import jp.me1han.sam.compat.TrainCompatRegistry;
import jp.me1han.sam.compat.TrainSnapshot;
import jp.me1han.sam.link.LinkKey;
import jp.me1han.sam.link.SamLinkedTile;
import jp.me1han.sam.link.SamLinkRegistry;
import jp.me1han.sam.trigger.SamTrigger;
import jp.me1han.sam.trigger.SamTriggerDispatcher;
import jp.me1han.sam.trigger.SamTriggerSourceType;
import jp.me1han.sam.trigger.SamTriggerType;


public class TileEntityStartAnnouncer extends RegisteredTileEntity implements SamLinkedTile {
    private String linkKey = "";
    public boolean isControlCar = false;
    private boolean lastPowered = false;
    private long lastFormationId = -1L;

    @Override
    public void updateEntity() {
        if (this.worldObj.isRemote) return;
        // 列車は高速で移動するため毎フレーム走査する。
        this.scanTrain();
    }

    private void scanTrain() {
        int r = 2;
        AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(xCoord - r, yCoord - r, zCoord - r, xCoord + r + 1, yCoord + r + 1, zCoord + r + 1);

        List<TrainSnapshot> list = TrainCompatRegistry.get().findTrains(this.worldObj, aabb);

        long currentFormationId = -1L;
        for (TrainSnapshot train : list) {
            if (this.isControlCar && !train.isControlCar()) continue;
            currentFormationId = train.getFormationId();
            break;
        }

        if (currentFormationId == -1L) {
            this.lastFormationId = -1L;
            return;
        }

        // ATSAssistModと同様に編成単位でトリガーする
        if (currentFormationId != this.lastFormationId) {
            this.lastFormationId = currentFormationId;
            this.dispatchTrigger(SamTriggerSourceType.TRAIN, currentFormationId);
        }
    }

    public void onRedstoneUpdate(boolean powered) {
        if (this.worldObj.isRemote) return;


        if (powered && !lastPowered) {
            this.dispatchTrigger(SamTriggerSourceType.REDSTONE, SamTrigger.NO_FORMATION);
        }
        this.lastPowered = powered;
    }

    private void dispatchTrigger(SamTriggerSourceType sourceType, long formationId) {
        SamTriggerDispatcher.dispatch(this.worldObj, SamTrigger.from(this, SamTriggerType.ANNOUNCE_START,
            this.getLinkKey(), sourceType, formationId));
    }

    @Override public String getLinkKey() { return this.linkKey; }
    @Override public void setLinkKey(String key) {
        this.linkKey = LinkKey.normalize(key);
        SamLinkRegistry.reindex(this);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", this.getLinkKey());
        nbt.setBoolean("isControlCar", this.isControlCar); // ★追加
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.setLinkKey(nbt.getString("linkKey"));
        this.isControlCar = nbt.getBoolean("isControlCar"); // ★追加
    }

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
