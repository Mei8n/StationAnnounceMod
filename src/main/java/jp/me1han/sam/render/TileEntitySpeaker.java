package jp.me1han.sam.render;

import jp.me1han.sam.StationAnnounceModCore;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileEntitySpeaker extends TileEntity {
    public String linkKey = "";
    public int range = 16;
    public float volume = 1.0f;

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", this.linkKey != null ? this.linkKey : "");
        nbt.setInteger("range", this.range);
        nbt.setFloat("volume", this.volume);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.linkKey = nbt.getString("linkKey");
        this.range = nbt.hasKey("range") ? nbt.getInteger("range") : 16;
        this.volume = nbt.hasKey("volume") ? nbt.getFloat("volume") : 1.0f;
        if (this.worldObj != null && this.worldObj.isRemote) {
            StationAnnounceModCore.logger.info("[SAM-DEBUG] Client speaker sync at " + this.xCoord + "," + this.yCoord + "," + this.zCoord + " key=[" + this.linkKey + "], range=" + this.range + ", volume=" + this.volume);
        }
    }

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
