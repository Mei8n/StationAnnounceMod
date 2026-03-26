package jp.me1han.sam.render;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import java.util.Map;

public class TileEntityDebugReceiver extends TileEntity {
    public String linkKey = "";
    private long lastReadTime = 0;
    private boolean lastPowered = false;

    @Override
    public void updateEntity() {
        if (this.worldObj.isRemote || this.linkKey == null || this.linkKey.isEmpty()) return;

        // 10次Tickごとに監視
        if (this.worldObj.getTotalWorldTime() % 10 == 0) {
            for (Object obj : this.worldObj.loadedTileEntityList) {
                if (obj instanceof TileEntityAnnouncer) {
                    TileEntityAnnouncer announcer = (TileEntityAnnouncer) obj;
                    // 保存されているlinkKeyと一致するかチェック
                    if (this.linkKey.equals(announcer.linkKey)) {
                        // 新しいデータが来た時だけ自動ログ
                        if (announcer.lastDataReceivedTime > this.lastReadTime) {
                            this.lastReadTime = announcer.lastDataReceivedTime;
                            this.printAnnouncerData(announcer, "§a[SAM-AUTO]");
                        }
                    }
                }
            }
        }
    }

    // RS信号による手動確認用
    public void onRedstoneUpdate(boolean powered) {
        if (powered && !lastPowered) {
            this.forcePrintData();
        }
        this.lastPowered = powered;
    }

    private void forcePrintData() {
        boolean found = false;
        for (Object obj : this.worldObj.loadedTileEntityList) {
            if (obj instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer announcer = (TileEntityAnnouncer) obj;
                if (this.linkKey.equals(announcer.linkKey)) {
                    this.printAnnouncerData(announcer, "§d[SAM-MANUAL]");
                    found = true;
                }
            }
        }
        if (!found) this.sendMessage("§c[SAM-DEBUG] No Announcer found with key: " + this.linkKey);
    }

    private void printAnnouncerData(TileEntityAnnouncer announcer, String prefix) {
        String header = prefix + " §fAnnouncer (" + announcer.xCoord + "," + announcer.yCoord + "," + announcer.zCoord + ")";
        this.sendMessage(header);

        if (announcer.receivedData == null || announcer.receivedData.isEmpty()) {
            this.sendMessage("  §7- (Data is Empty/Cleared)");
        } else {
            for (Map.Entry<String, String> entry : announcer.receivedData.entrySet()) {
                this.sendMessage("  §7- " + entry.getKey() + " : §f" + entry.getValue());
            }
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
        if (this.linkKey != null) nbt.setString("linkKey", this.linkKey);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.linkKey = nbt.getString("linkKey");
    }

    // ★同期用：これがないとGUIで設定したキーが保存されません
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
