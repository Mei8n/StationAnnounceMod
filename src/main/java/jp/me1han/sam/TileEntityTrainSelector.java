package jp.me1han.sam;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import java.util.List;
import cpw.mods.fml.common.Loader;

public class TileEntityTrainSelector extends TileEntity {
    private String linkKey = "";      // 放送装置との紐付けキー
    private String dataKey = "state"; // DataMapのキー名
    private int dataType = 0;         // 0:String, 1:Int, 2:Boolean, 3:Double
    private int cooldown = 0;

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        if (cooldown > 0) { cooldown--; return; }

        if (Loader.isModLoaded("RTM")) {
            detectAndProcess();
        }
    }

    private void detectAndProcess() {
        // ブロックの上方2ブロック分を検知
        AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 2, zCoord + 1);
        List entities = worldObj.getEntitiesWithinAABBExcludingEntity(null, aabb);

        for (Object obj : entities) {
            if (RTMCompat.isTrain(obj)) {
                String value = RTMCompat.getDataMapValue(obj, dataKey, dataType);
                if (value != null) {
                    // リンクキーに基づいてデータを送信（パケット送信処理へ）
                    NetworkHandler.sendTrainData(this.linkKey, value);
                    this.cooldown = 100; // 重複防止のため5秒待機
                    break;
                }
            }
        }
    }

    // 各種Getter/SetterとNBT保存
    public String getLinkKey() { return linkKey; }
    public void setLinkKey(String s) { linkKey = s; }
    public String getDataKey() { return dataKey; }
    public void setDataKey(String s) { dataKey = s; }
    public int getDataType() { return dataType; }
    public void setDataType(int i) { dataType = i; }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        linkKey = nbt.getString("linkKey");
        dataKey = nbt.getString("dataKey");
        dataType = nbt.getInteger("dataType");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", linkKey);
        nbt.setString("dataKey", dataKey);
        nbt.setInteger("dataType", dataType);
    }
}
