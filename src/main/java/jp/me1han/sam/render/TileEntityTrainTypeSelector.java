package jp.me1han.sam.render;

import jp.me1han.sam.api.TrainTypeCondition;
import jp.me1han.sam.compat.TrainCompatRegistry;
import jp.me1han.sam.compat.TrainSnapshot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import jp.me1han.sam.link.LinkKey;
import jp.me1han.sam.link.SamLinkedTile;
import jp.me1han.sam.link.SamLinkRegistry;

public class TileEntityTrainTypeSelector extends RegisteredTileEntity implements SamLinkedTile {
    private String linkKey = "";
    public boolean isControlCar = false;
    public List<TrainTypeCondition> conditions = new ArrayList<TrainTypeCondition>();
    public Map<String, String> extractedData = new HashMap<String, String>();
    private int signalTicks = 0;
    private int lastTrainId = -1;

    @Override
    public void updateEntity() {
        if (this.worldObj.isRemote) return;

        if (signalTicks > 0) {
            signalTicks--;
            if (signalTicks == 0) {
                this.worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, this.getBlockType());
            }
        }

        this.scanAndExtractTrain();
    }

    private void scanAndExtractTrain() {
        if (extractedData != null) extractedData.clear();

        int r = 2;
        AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(xCoord - r, yCoord - r, zCoord - r, xCoord + r + 1, yCoord + r + 1, zCoord + r + 1);
        List<TrainSnapshot> list = TrainCompatRegistry.get().findTrains(this.worldObj, aabb);

        if (list.isEmpty()) {
            this.lastTrainId = -1;
            return;
        }

        TrainSnapshot train = list.get(0);
        if (train.getEntityId() == this.lastTrainId) return;
        this.lastTrainId = train.getEntityId();

        if (this.isControlCar && !train.isControlCar()) return;

        for (TrainTypeCondition cond : conditions) {
            String val = train.extractData(cond.key, cond.type);
            if (val != null && !val.isEmpty()) {
                extractedData.put(cond.key, val);
            }
        }

        if (!this.extractedData.isEmpty()) {
            this.dispatchData(this.extractedData);
            this.extractedData.clear();
        }
    }

    public void dispatchData(Map<String, String> dataMap) {
        if (this.worldObj.isRemote || LinkKey.isEmpty(this.getLinkKey())) return;

        String sourcePos = String.format("%d, %d, %d", xCoord, yCoord, zCoord);

        for (TileEntityAnnouncer receiver : SamLinkRegistry.findAll(this.worldObj, this.getLinkKey(), TileEntityAnnouncer.class)) {
            receiver.onDataReceived(dataMap, sourcePos);
        }
    }

    @Override public String getLinkKey() { return this.linkKey; }
    @Override public void setLinkKey(String key) {
        this.linkKey = LinkKey.normalize(key);
        SamLinkRegistry.reindex(this);
    }

    public int getPowerOutput() {
        return signalTicks > 0 ? 15 : 0;
    }

    public boolean isUseableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
        return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) != this ? false :
            player.getDistanceSq((double)this.xCoord + 0.5D, (double)this.yCoord + 0.5D, (double)this.zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", this.getLinkKey());
        nbt.setBoolean("isControlCar", this.isControlCar);

        NBTTagList list = new NBTTagList();
        for (TrainTypeCondition cond : conditions) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("k", cond.key);
            tag.setInteger("t", cond.type);
            list.appendTag(tag);
        }
        nbt.setTag("conditions", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.setLinkKey(nbt.getString("linkKey"));
        this.isControlCar = nbt.getBoolean("isControlCar");

        if (nbt.hasKey("conditions", 9)) {
            NBTTagList list = nbt.getTagList("conditions", 10);
            this.conditions = new ArrayList<TrainTypeCondition>();
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                String k = tag.getString("k");
                int t = tag.getInteger("t");
                this.conditions.add(new TrainTypeCondition(k, t));
            }
        }
    }

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
