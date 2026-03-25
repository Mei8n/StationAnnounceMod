package jp.me1han.sam.render;

import cpw.mods.fml.common.Loader;
import jp.me1han.sam.api.TrainTypeCondition;
import jp.me1han.sam.StationAnnounceModCore;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class TileEntityTrainTypeSelector extends TileEntity {
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

        if (Loader.isModLoaded("RTM")) {
            this.scanAndExtractTrain();
        }
    }

    private void scanAndExtractTrain() {
        AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(
            (double)xCoord, (double)yCoord + 1, (double)zCoord,
            (double)xCoord + 1, (double)yCoord + 4, (double)zCoord + 1
        );

        List list = this.worldObj.getEntitiesWithinAABB(net.minecraft.entity.Entity.class, aabb);

        boolean foundNewTrain = false;
        for (Object obj : list) {
            net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) obj;
            if (entity.getClass().getName().contains("EntityTrainBase")) {

                // すでに処理済みの車両ならスキップ（return ではなく continue にする）
                if (entity.getEntityId() == lastTrainId) continue;

                lastTrainId = entity.getEntityId();
                this.extractData(entity);
                foundNewTrain = true;

                // 抽出成功時にのみパルスを出す
                this.signalTicks = 20;
                this.worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, this.getBlockType());

                // ループを抜ける（1編成につき1回処理）
                break;
            }
        }
    }

    private void extractData(net.minecraft.entity.Entity train) {
        extractedData.clear();
        net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
        train.writeToNBT(nbt);
        net.minecraft.nbt.NBTTagCompound trainState = nbt.getCompoundTag("ModelTrainState");

        for (TrainTypeCondition cond : conditions) {
            String val = "";

            if (trainState.hasKey(cond.key)) {
                val = trainState.getTag(cond.key).toString().replace("\"", "");
            } else if (nbt.hasKey(cond.key)) {
                val = nbt.getTag(cond.key).toString().replace("\"", "");
            }

            if (!val.isEmpty()) {
                extractedData.put(cond.key, val);
                // StationAnnounceModCore.logger.info("Extracted: " + cond.key + " = " + val);
            }
        }
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
        NBTTagList list = nbt.getTagList("conditions", 10);
        this.conditions = new ArrayList<TrainTypeCondition>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            this.conditions.add(new TrainTypeCondition(tag.getString("k"), tag.getInteger("t")));
        }
    }
}
