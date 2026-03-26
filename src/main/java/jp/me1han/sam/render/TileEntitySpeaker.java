package jp.me1han.sam.render;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileEntitySpeaker extends TileEntity {
    public String linkKey = "";
    public int range = 32;     // 可聴範囲 (ブロック)
    public float volume = 1.0F; // 音量 (0.0 - 1.0)

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", linkKey);
        nbt.setInteger("range", range);
        nbt.setFloat("volume", volume);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.linkKey = nbt.getString("linkKey");
        this.range = nbt.getInteger("range");
        this.volume = nbt.getFloat("volume");
    }

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
