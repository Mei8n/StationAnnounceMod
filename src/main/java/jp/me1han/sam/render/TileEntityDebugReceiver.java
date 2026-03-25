package jp.me1han.sam.render;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import java.util.Map;

public class TileEntityDebugReceiver extends TileEntity {
    public String linkKey = "";
    private long lastReadTime = 0;

    @Override
    public void updateEntity() {
        if (this.worldObj.isRemote || this.linkKey == null || this.linkKey.isEmpty()) return;

        if (this.worldObj.getTotalWorldTime() % 10 != 0) return;

        for (Object obj : this.worldObj.loadedTileEntityList) {
            if (obj instanceof jp.me1han.sam.render.TileEntityAnnouncer) {
                jp.me1han.sam.render.TileEntityAnnouncer announcer = (jp.me1han.sam.render.TileEntityAnnouncer) obj;

                if (this.linkKey.equals(announcer.linkKey)) {

                    if (announcer.lastDataReceivedTime > this.lastReadTime) {
                        this.lastReadTime = announcer.lastDataReceivedTime;
                        this.printAnnouncerData(announcer);
                    }
                }
            }
        }
    }

    private void printAnnouncerData(jp.me1han.sam.render.TileEntityAnnouncer announcer) {
        String header = "§d[SAM-DEBUG] Read from Announcer at " + announcer.xCoord + ", " + announcer.yCoord + ", " + announcer.zCoord + " (Key: " + this.linkKey + ")";
        this.sendMessage(header);

        if (announcer.receivedData.isEmpty()) {
            this.sendMessage("  §7- (No Data)");
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

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
