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
    private int lastTrainId = -1;

    @Override
    public void updateEntity() {
        if (this.worldObj.isRemote) return;
        if (Loader.isModLoaded("RTM")) {
            this.scanTrain();
        }
    }

    private void scanTrain() {
        int r = 2;
        AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(xCoord - r, yCoord - r, zCoord - r, xCoord + r + 1, yCoord + r + 1, zCoord + r + 1);

        List list = this.worldObj.getEntitiesWithinAABB(net.minecraft.entity.Entity.class, aabb);

        int currentTrainId = -1;

        for (Object obj : list) {
            if (obj == null) continue;
            String className = obj.getClass().getName();
            if (className.equals("jp.ngt.rtm.entity.train.EntityTrainBase") || className.contains("EntityTrain")) {
                net.minecraft.entity.Entity train = (net.minecraft.entity.Entity) obj;
                currentTrainId = train.getEntityId();
                break;
            }
        }

        if (currentTrainId == -1) {
            this.lastTrainId = -1;
            return;
        }

        if (currentTrainId != this.lastTrainId) {
            this.lastTrainId = currentTrainId;
            this.dispatchStopTrigger();
        }
    }

    public void onRedstoneUpdate(boolean powered) {
        if (this.worldObj.isRemote) return;
        if (powered && !lastPowered) {
            this.dispatchStopTrigger();
        }
        this.lastPowered = powered;
    }

    private void dispatchStopTrigger() {
        if (this.linkKey == null || this.linkKey.isEmpty()) return;
        for (Object obj : this.worldObj.loadedTileEntityList) {
            if (obj instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer announcer = (TileEntityAnnouncer) obj;
                if (this.linkKey.equals(announcer.linkKey)) {
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
