package jp.me1han.sam.render;

import cpw.mods.fml.common.Loader;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import java.util.List;
import jp.me1han.sam.link.LinkKey;
import jp.me1han.sam.link.SamLinkedTile;
import jp.me1han.sam.link.SamLinkRegistry;
import jp.me1han.sam.trigger.SamTrigger;
import jp.me1han.sam.trigger.SamTriggerDispatcher;
import jp.me1han.sam.trigger.SamTriggerSourceType;
import jp.me1han.sam.trigger.SamTriggerType;

public class TileEntityStopAnnouncer extends RegisteredTileEntity implements SamLinkedTile {
    public String linkKey = "";
    public boolean isControlCar = false;
    private boolean lastPowered = false;
    private long lastFormationId = -1L;

    @Override
    public void updateEntity() {
        if (this.worldObj.isRemote) return;
        if (Loader.isModLoaded("RTM")) {
            this.scanTrain();
        }
    }

    @SuppressWarnings("unchecked")
    private void scanTrain() {
        int r = 2;
        AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(xCoord - r, yCoord - r, zCoord - r, xCoord + r + 1, yCoord + r + 1, zCoord + r + 1);

        List<jp.ngt.rtm.entity.train.EntityTrainBase> list = (List<jp.ngt.rtm.entity.train.EntityTrainBase>) this.worldObj.getEntitiesWithinAABB(jp.ngt.rtm.entity.train.EntityTrainBase.class, aabb);

        long currentFormationId = -1L;
        for (jp.ngt.rtm.entity.train.EntityTrainBase train : list) {
            if (this.isControlCar && !this.isControlCar(train)) continue;
            currentFormationId = this.resolveFormationId(train);
            break;
        }

        if (currentFormationId == -1L) {
            this.lastFormationId = -1L;
            return;
        }

        if (currentFormationId != this.lastFormationId) {
            this.lastFormationId = currentFormationId;
            this.dispatchStopTrigger(SamTriggerSourceType.TRAIN, currentFormationId);
        }
    }

    private boolean isControlCar(jp.ngt.rtm.entity.train.EntityTrainBase train) {
        return train.isControlCar();
    }

    private long resolveFormationId(jp.ngt.rtm.entity.train.EntityTrainBase train) {
        try {
            if (train.getFormation() != null) {
                return train.getFormation().id;
            }
        } catch (Exception e) {
            // Fallback to entity id when formation info is unavailable.
        }
        return train.getEntityId();
    }

    public void onRedstoneUpdate(boolean powered) {
        if (this.worldObj.isRemote) return;


        if (powered && !lastPowered) {
            this.dispatchStopTrigger(SamTriggerSourceType.REDSTONE, SamTrigger.NO_FORMATION);
        }
        this.lastPowered = powered;
    }

    private void dispatchStopTrigger(SamTriggerSourceType sourceType, long formationId) {
        SamTriggerDispatcher.dispatch(this.worldObj, SamTrigger.from(this, SamTriggerType.ANNOUNCE_STOP,
            this.linkKey, sourceType, formationId));
    }

    @Override public String getLinkKey() { return this.linkKey; }
    @Override public void setLinkKey(String key) {
        this.linkKey = LinkKey.normalize(key);
        SamLinkRegistry.reindex(this);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (this.linkKey != null) nbt.setString("linkKey", this.linkKey);
        nbt.setBoolean("isControlCar", this.isControlCar);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.setLinkKey(nbt.getString("linkKey"));
        this.isControlCar = nbt.getBoolean("isControlCar");
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
