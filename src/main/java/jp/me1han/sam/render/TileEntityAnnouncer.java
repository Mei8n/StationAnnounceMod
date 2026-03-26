package jp.me1han.sam.render;

import jp.me1han.sam.api.AnnounceData;
import jp.me1han.sam.network.PacketAnnounce;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.AnnouncePackLoader;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import cpw.mods.fml.common.network.NetworkRegistry;
import java.util.Map;
import java.util.HashMap;

public class TileEntityAnnouncer extends TileEntity {
    private boolean lastPowered = false;
    private String scriptName = "";
    public String linkKey = "";

    public Map<String, String> receivedData = new HashMap<String, String>();
    public long lastDataReceivedTime = 0;

    public void onRedstoneUpdate(boolean powered) {
        if (this.worldObj.isRemote) return;

        if (powered && !lastPowered) {
            startAnnounce();
        }

        this.lastPowered = powered;
    }

    public void startAnnounce() {
        if (scriptName == null || scriptName.isEmpty()) return;

        AnnounceData data = AnnouncePackLoader.runScript(scriptName, this);

        this.receivedData.clear();
        this.lastDataReceivedTime = 0;
        this.markDirty();

        if (data != null) {
            NetworkHandler.INSTANCE.sendToAllAround(
                new PacketAnnounce(data, this.linkKey),
                new NetworkRegistry.TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 64)
            );
        }
    }

    public void forceStop() {
        if (this.worldObj.isRemote) return;
        NetworkHandler.INSTANCE.sendToAllAround(
            new PacketAnnounce(true, this.linkKey),
            new NetworkRegistry.TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 64)
        );
    }

    public void onDataReceived(Map<String, String> data, String sourcePos) {
        if (this.worldObj.isRemote) return;

        this.receivedData = new HashMap<String, String>(data);
        this.lastDataReceivedTime = System.currentTimeMillis();
    }

    public String getScriptName() { return this.scriptName; }
    public void setScriptName(String name) {
        this.scriptName = name;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (this.scriptName != null) nbt.setString("scriptName", this.scriptName);
        if (this.linkKey != null) {
            nbt.setString("linkKey", this.linkKey);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.scriptName = nbt.getString("scriptName");
        this.linkKey = nbt.getString("linkKey");
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

    public boolean isUseableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
        return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) != this ? false :
            player.getDistanceSq((double)this.xCoord + 0.5D, (double)this.yCoord + 0.5D, (double)this.zCoord + 0.5D) <= 64.0D;
    }
}
