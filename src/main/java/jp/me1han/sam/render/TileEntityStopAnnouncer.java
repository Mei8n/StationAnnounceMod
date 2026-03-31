package jp.me1han.sam.render;

import cpw.mods.fml.common.Loader;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import java.util.List;

public class TileEntityStopAnnouncer extends TileEntity {
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
            this.dispatchStopTrigger();
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

        jp.me1han.sam.network.NetworkHandler.sendDebugMessage(this.worldObj, this.linkKey, "[SAM-DEBUG] StopAnnouncer RS Update: powered=" + powered + ", lastPowered=" + lastPowered);

        if (powered && !lastPowered) {
            this.dispatchStopTrigger();
        }
        this.lastPowered = powered;
    }

    private void dispatchStopTrigger() {
        if (this.linkKey == null || this.linkKey.isEmpty()) return;
        String normalizedKey = this.linkKey.trim();
        for (Object obj : this.worldObj.loadedTileEntityList) {
            if (obj instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer announcer = (TileEntityAnnouncer) obj;
                if (announcer.linkKey != null && !announcer.linkKey.trim().isEmpty() &&
                    normalizedKey.equals(announcer.linkKey.trim())) {
                    announcer.forceStop();
                }
            }
        }
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
        this.linkKey = nbt.getString("linkKey");
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
