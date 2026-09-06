package jp.me1han.sam.render;

import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.network.PacketAnnounce;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityDepartureMelody extends TileEntity {
    public String linkKey = "";
    public String soundId = "";

    private boolean lastPowered = false;
    private int completionTicks = -1;

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote || this.completionTicks < 0) {
            return;
        }

        if (this.completionTicks == 0) {
            TileEntityAnnouncer parent = findParent();
            if (parent != null) {
                parent.notifyDepartureMelodyFinished();
            }
            this.completionTicks = -1;
        } else {
            this.completionTicks--;
        }
    }

    public void onRedstoneUpdate(boolean powered) {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }
        if (powered && !this.lastPowered) {
            startMelody();
        }
        this.lastPowered = powered;
    }

    public void startMelody() {
        String sound = this.soundId == null ? "" : this.soundId.trim();
        TileEntityAnnouncer parent = findParent();
        if (parent == null || sound.isEmpty()) {
            return;
        }

        parent.startDirectSound(sound, PacketAnnounce.PRIORITY_DEPARTURE_MELODY, false);
        Integer duration = AnnouncePackLoader.soundTicks.get(sound);
        this.completionTicks = duration == null ? 20 : Math.max(1, duration);
        this.markDirty();
    }

    public void applyConfig(String linkKey, String soundId) {
        this.linkKey = linkKey == null ? "" : linkKey.trim();
        this.soundId = soundId == null ? "" : soundId.trim();
        this.completionTicks = -1;
    }

    private TileEntityAnnouncer findParent() {
        if (this.worldObj == null) {
            return null;
        }
        String key = this.linkKey == null ? "" : this.linkKey.trim();
        if (key.isEmpty()) {
            return null;
        }

        for (Object obj : this.worldObj.loadedTileEntityList) {
            if (obj instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer parent = (TileEntityAnnouncer) obj;
                String parentKey = parent.linkKey == null ? "" : parent.linkKey.trim();
                if (key.equals(parentKey)) {
                    return parent;
                }
            }
        }
        return null;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", this.linkKey == null ? "" : this.linkKey);
        nbt.setString("soundId", this.soundId == null ? "" : this.soundId);
        nbt.setBoolean("lastPowered", this.lastPowered);
        nbt.setInteger("completionTicks", this.completionTicks);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.linkKey = nbt.getString("linkKey");
        this.soundId = nbt.getString("soundId");
        this.lastPowered = nbt.getBoolean("lastPowered");
        this.completionTicks = nbt.hasKey("completionTicks") ? nbt.getInteger("completionTicks") : -1;
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
