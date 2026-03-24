package jp.me1han.sam;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileEntityStopAnnouncer extends TileEntity {
    private String linkKey = "";

    public String getLinkKey() { return linkKey; }
    public void setLinkKey(String key) {
        this.linkKey = key;
        this.markDirty(); // データの変更を保存対象にする
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.linkKey = nbt.getString("linkKey");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", this.linkKey);
    }

    // クライアント・サーバー間の同期用パケット
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
