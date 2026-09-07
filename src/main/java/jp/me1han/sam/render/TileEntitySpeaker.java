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

        this.range = Math.max(1, Math.min(jp.me1han.sam.network.PacketLimits.MAX_RANGE, this.range));
        this.volume = Float.isNaN(this.volume) || Float.isInfinite(this.volume) ? 1.0F
            : Math.max(0, Math.min(jp.me1han.sam.network.PacketLimits.MAX_VOLUME, this.volume));
        syncRegistry();
    }

    @Override
    public boolean canUpdate() { return false; }

    @Override
    public void validate() {
        super.validate();
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

    public boolean applyConfig(String key, int range, float volume) {
        key = SpeakerRegistry.normalize(key);
        if (!jp.me1han.sam.network.PacketLimits.string(key, jp.me1han.sam.network.PacketLimits.LINK_KEY)
            || !jp.me1han.sam.network.PacketLimits.speaker(range, volume)) return false;
        if (key.equals(linkKey) && this.range == range && this.volume == volume) return false;
        linkKey = key; this.range = range; this.volume = volume;
        syncRegistry();
        markDirty();
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return true;
    }

    private void syncRegistry() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }

        SpeakerRegistry.register(this);
    }

    private void removeFromRegistry() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }

        SpeakerRegistry.unregister(this);
    }
}
