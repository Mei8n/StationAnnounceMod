package jp.me1han.sam.render;

import cpw.mods.fml.common.Loader;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import java.util.List;

public class TileEntityStartAnnouncer extends TileEntity {
    public String linkKey = "";
    private boolean lastPowered = false;
    private int lastTrainId = -1;

    @Override
    public void updateEntity() {
        if (this.worldObj.isRemote) return;

        // RTMが導入されている場合のみ車両検知を行う
        if (Loader.isModLoaded("RTM")) {
            this.scanTrain();
        }
    }

    private void scanTrain() {
        // 検知範囲（周囲2ブロック）
        int r = 2;
        AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(xCoord - r, yCoord - r, zCoord - r, xCoord + r + 1, yCoord + r + 1, zCoord + r + 1);
        List list = this.worldObj.getEntitiesWithinAABB(jp.ngt.rtm.entity.train.EntityTrainBase.class, aabb);

        if (list.isEmpty()) {
            this.lastTrainId = -1;
            return;
        }

        jp.ngt.rtm.entity.train.EntityTrainBase train = (jp.ngt.rtm.entity.train.EntityTrainBase) list.get(0);

        // 新しい車両を検知した瞬間だけトリガーを送る
        if (train.getEntityId() != this.lastTrainId) {
            this.lastTrainId = train.getEntityId();
            this.dispatchTrigger();
        }
    }

    public void onRedstoneUpdate(boolean powered) {
        if (this.worldObj.isRemote) return;

        // レッドストーン信号の立ち上がり（OFF -> ON）でトリガー
        if (powered && !lastPowered) {
            this.dispatchTrigger();
        }
        this.lastPowered = powered;
    }

    private void dispatchTrigger() {
        if (this.linkKey == null || this.linkKey.isEmpty()) return;

        // ワールド内のロードされているTileEntityから放送装置を探す
        for (Object obj : this.worldObj.loadedTileEntityList) {
            if (obj instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer announcer = (TileEntityAnnouncer) obj;
                if (this.linkKey.equals(announcer.linkKey)) {
                    // 放送装置に「再生開始」を命令
                    announcer.startAnnounce();
                }
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (this.linkKey != null) nbt.setString("linkKey", this.linkKey);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.linkKey = nbt.getString("linkKey");
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
