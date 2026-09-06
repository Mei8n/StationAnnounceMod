package jp.me1han.sam.render;

import jp.me1han.sam.network.PacketAnnounce;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.List;

public class TileEntityAwarenessAnnouncer extends TileEntity {
    public String linkKey = "";
    public String soundList = "";
    public int intervalTicks = 1200;
    public boolean randomOrder = false;
    public boolean allowOverlap = false;
    public boolean playAfterDeparture = false;
    public int departureDelayTicks = 100;

    private int ticksUntilNext = 1200;
    private int pendingDepartureTicks = -1;
    private int nextSoundIndex = 0;

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }

        boolean played = false;
        if (this.pendingDepartureTicks >= 0) {
            if (this.pendingDepartureTicks == 0) {
                played = playNextSound();
                this.pendingDepartureTicks = -1;
            } else {
                this.pendingDepartureTicks--;
            }
        }

        if (this.ticksUntilNext <= 0) {
            if (!played) {
                playNextSound();
            }
            this.ticksUntilNext = getSafeIntervalTicks();
        } else {
            this.ticksUntilNext--;
        }
    }

    public void applyConfig(String linkKey, String soundList, int intervalTicks, boolean randomOrder,
                            boolean allowOverlap, boolean playAfterDeparture, int departureDelayTicks) {
        this.linkKey = linkKey == null ? "" : linkKey.trim();
        this.soundList = normalizeSoundList(soundList);
        this.intervalTicks = Math.max(20, intervalTicks);
        this.randomOrder = randomOrder;
        this.allowOverlap = allowOverlap;
        this.playAfterDeparture = playAfterDeparture;
        this.departureDelayTicks = Math.max(0, departureDelayTicks);
        this.ticksUntilNext = getSafeIntervalTicks();
        this.pendingDepartureTicks = -1;
        this.nextSoundIndex = 0;
    }

    public void scheduleAfterDeparture() {
        if (this.playAfterDeparture) {
            this.pendingDepartureTicks = Math.max(0, this.departureDelayTicks);
            this.markDirty();
        }
    }

    public String getNormalizedLinkKey() {
        return this.linkKey == null ? "" : this.linkKey.trim();
    }

    private boolean playNextSound() {
        List<String> sounds = getSounds();
        if (sounds.isEmpty()) {
            return false;
        }

        TileEntityAnnouncer parent = findParent();
        if (parent == null) {
            return false;
        }

        int index;
        if (this.randomOrder && sounds.size() > 1) {
            index = this.worldObj.rand.nextInt(sounds.size());
        } else {
            index = this.nextSoundIndex % sounds.size();
            this.nextSoundIndex = (index + 1) % sounds.size();
        }

        parent.startDirectSound(sounds.get(index), PacketAnnounce.PRIORITY_AWARENESS, this.allowOverlap);
        this.markDirty();
        return true;
    }

    private TileEntityAnnouncer findParent() {
        String key = getNormalizedLinkKey();
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

    public List<String> getSounds() {
        List<String> result = new ArrayList<String>();
        if (this.soundList == null || this.soundList.trim().isEmpty()) {
            return result;
        }

        String[] values = this.soundList.split("[,;\\r\\n]+");
        for (String value : values) {
            String sound = value == null ? "" : value.trim();
            if (!sound.isEmpty()) {
                result.add(sound);
            }
        }
        return result;
    }

    private String normalizeSoundList(String value) {
        List<String> sounds = new ArrayList<String>();
        if (value != null) {
            String[] values = value.split("[,;\\r\\n]+");
            for (String item : values) {
                String sound = item == null ? "" : item.trim();
                if (!sound.isEmpty()) {
                    sounds.add(sound);
                }
            }
        }

        StringBuilder result = new StringBuilder();
        for (String sound : sounds) {
            if (result.length() > 0) {
                result.append(",");
            }
            result.append(sound);
        }
        return result.toString();
    }

    private int getSafeIntervalTicks() {
        return Math.max(20, this.intervalTicks);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", this.linkKey == null ? "" : this.linkKey);
        nbt.setString("soundList", this.soundList == null ? "" : this.soundList);
        nbt.setInteger("intervalTicks", this.intervalTicks);
        nbt.setBoolean("randomOrder", this.randomOrder);
        nbt.setBoolean("allowOverlap", this.allowOverlap);
        nbt.setBoolean("playAfterDeparture", this.playAfterDeparture);
        nbt.setInteger("departureDelayTicks", this.departureDelayTicks);
        nbt.setInteger("ticksUntilNext", this.ticksUntilNext);
        nbt.setInteger("pendingDepartureTicks", this.pendingDepartureTicks);
        nbt.setInteger("nextSoundIndex", this.nextSoundIndex);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.linkKey = nbt.getString("linkKey");
        this.soundList = nbt.getString("soundList");
        this.intervalTicks = nbt.hasKey("intervalTicks") ? Math.max(20, nbt.getInteger("intervalTicks")) : 1200;
        this.randomOrder = nbt.getBoolean("randomOrder");
        this.allowOverlap = nbt.getBoolean("allowOverlap");
        this.playAfterDeparture = nbt.getBoolean("playAfterDeparture");
        this.departureDelayTicks = nbt.hasKey("departureDelayTicks") ? Math.max(0, nbt.getInteger("departureDelayTicks")) : 100;
        this.ticksUntilNext = nbt.hasKey("ticksUntilNext") ? Math.max(0, nbt.getInteger("ticksUntilNext")) : this.intervalTicks;
        this.pendingDepartureTicks = nbt.hasKey("pendingDepartureTicks") ? nbt.getInteger("pendingDepartureTicks") : -1;
        this.nextSoundIndex = Math.max(0, nbt.getInteger("nextSoundIndex"));
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
