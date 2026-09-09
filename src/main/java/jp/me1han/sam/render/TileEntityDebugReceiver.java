package jp.me1han.sam.render;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import java.util.Map;
import jp.me1han.sam.link.LinkKey;
import jp.me1han.sam.link.SamLinkedTile;
import jp.me1han.sam.link.SamLinkRegistry;

public class TileEntityDebugReceiver extends RegisteredTileEntity implements SamLinkedTile {
    private String linkKey = "";
    private long lastReadTime = 0;
    private boolean lastPowered = false;
    private long lastCacheTime = 0;
    private static final long CACHE_DURATION = 100; // Ticks（約5秒）

    @Override
    public void updateEntity() {
        if (this.worldObj.isRemote || LinkKey.isEmpty(this.getLinkKey())) return;

        // 100フレーム毎にスキャン（毎10フレームから大幅削減）
        if (this.worldObj.getTotalWorldTime() - this.lastCacheTime > CACHE_DURATION) {
            this.lastCacheTime = this.worldObj.getTotalWorldTime();
            this.scanForUpdates();
        }
    }

    private void scanForUpdates() {
        for (TileEntityAnnouncer announcer : SamLinkRegistry.findAll(this.worldObj, this.getLinkKey(), TileEntityAnnouncer.class)) {
            if (announcer.lastDataReceivedTime > this.lastReadTime) {
                this.lastReadTime = announcer.lastDataReceivedTime;
                this.printAnnouncerData(announcer);
            }
        }
    }

    public void onRedstoneUpdate(boolean powered) {
        if (this.worldObj == null || this.worldObj.isRemote) {
            this.lastPowered = powered;
            return;
        }

        if (LinkKey.isEmpty(this.getLinkKey())) {
            this.lastPowered = powered;
            return;
        }

        if (powered && !lastPowered) {
            this.forcePrintData();
        }
        this.lastPowered = powered;
    }

    private void forcePrintData() {
        if (LinkKey.isEmpty(this.getLinkKey())) return;
        for (TileEntityAnnouncer announcer : SamLinkRegistry.findAll(this.worldObj, this.getLinkKey(), TileEntityAnnouncer.class)) {
            this.printAnnouncerData(announcer);
        }
    }

    @Override public String getLinkKey() { return this.linkKey; }
    @Override public void setLinkKey(String key) {
        this.linkKey = LinkKey.normalize(key);
        SamLinkRegistry.reindex(this);
    }

    private void printAnnouncerData(TileEntityAnnouncer announcer) {
        if (announcer.receivedData == null || announcer.receivedData.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : announcer.receivedData.entrySet()) {
            this.sendMessage("  §7- " + entry.getKey() + " : §f" + entry.getValue());
        }
    }

    private void sendMessage(String text) {
        for (Object obj : this.worldObj.playerEntities) {
            ((net.minecraft.entity.player.EntityPlayer) obj).addChatMessage(new ChatComponentText(text));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", this.getLinkKey());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.setLinkKey(nbt.getString("linkKey"));
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
