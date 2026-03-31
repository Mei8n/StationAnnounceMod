package jp.me1han.sam.render;

import jp.me1han.sam.SpeakerRegistry;
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
        String key = nbt.getString("linkKey");
        this.linkKey = key == null ? "" : key.trim();
        this.range = nbt.hasKey("range") ? nbt.getInteger("range") : 16;
        this.volume = nbt.hasKey("volume") ? nbt.getFloat("volume") : 1.0f;

        syncRegistry();
    }

    @Override
    public void updateEntity() {
        syncRegistry();
    }

    @Override
    public void invalidate() {
        removeFromRegistry();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        removeFromRegistry();
        super.onChunkUnload();
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

    private void syncRegistry() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }

        SpeakerRegistry.upsert(
            this.worldObj.provider.dimensionId,
            this.xCoord,
            this.yCoord,
            this.zCoord,
            this.linkKey,
            this.range,
            this.volume
        );
    }

    private void removeFromRegistry() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }

        SpeakerRegistry.removeAt(this.worldObj.provider.dimensionId, this.xCoord, this.yCoord, this.zCoord);
    }
}
