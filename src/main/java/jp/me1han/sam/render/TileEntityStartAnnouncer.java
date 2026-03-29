package jp.me1han.sam.render;

import cpw.mods.fml.common.Loader;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import java.util.List;


public class TileEntityStartAnnouncer extends TileEntity {
    public String linkKey = "";
    public boolean isControlCar = false;
    private boolean lastPowered = false;
    private int lastTrainId = -1;

    @Override
    public void updateEntity() {
        if (this.worldObj.isRemote) return;
        if (Loader.isModLoaded("RTM")) {
            // RTM車両は高速で移動するため毎フレーム走査が必須
            // （10フレーム周期では見落とされる可能性がある）
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

        // 列車ID変更時のみトリガー（状態変化時のみ処理）
        if (currentTrainId != this.lastTrainId) {
            this.lastTrainId = currentTrainId;
            this.dispatchTrigger();
        }
    }

    public void onRedstoneUpdate(boolean powered) {
        if (this.worldObj.isRemote) return;

        jp.me1han.sam.network.NetworkHandler.sendDebugMessage(this.worldObj, this.linkKey, "[SAM-DEBUG] StartAnnouncer RS Update: powered=" + powered + ", lastPowered=" + lastPowered);

        if (powered && !lastPowered) {
            this.dispatchTrigger();
        }
        this.lastPowered = powered;
    }

    private void dispatchTrigger() {
        if (this.linkKey == null || this.linkKey.trim().isEmpty()) {
            return;
        }

        String normalizedKey = this.linkKey.trim();
        boolean foundAnnouncer = false;

        for (Object obj : this.worldObj.loadedTileEntityList) {
            if (obj instanceof jp.me1han.sam.render.TileEntityAnnouncer) {
                foundAnnouncer = true;
                jp.me1han.sam.render.TileEntityAnnouncer announcer = (jp.me1han.sam.render.TileEntityAnnouncer) obj;

                if (announcer.linkKey != null && !announcer.linkKey.trim().isEmpty() &&
                    normalizedKey.equals(announcer.linkKey.trim())) {
                    // デバッグレシーバーが存在する場合のみチャットに出力
                    jp.me1han.sam.network.NetworkHandler.sendDebugMessage(this.worldObj, normalizedKey, "[SAM-DEBUG] StartAnnouncer triggered! linkKey=[" + normalizedKey + "]");
                    announcer.startAnnounce();
                    return;
                }
            }
        }

        // TileEntityAnnouncerが見つからない場合もログ出力
        if (!foundAnnouncer) {
            jp.me1han.sam.network.NetworkHandler.sendDebugMessage(this.worldObj, normalizedKey, "[SAM-DEBUG] ERROR: No TileEntityAnnouncer found!");
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (this.linkKey != null) nbt.setString("linkKey", this.linkKey);
        nbt.setBoolean("isControlCar", this.isControlCar); // ★追加
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.linkKey = nbt.getString("linkKey");
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
