package jp.me1han.sam.render;

import jp.me1han.sam.api.AnnounceData;
import jp.me1han.sam.network.PacketAnnounce;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.AnnouncePackLoader;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import java.util.Map;
import java.util.HashMap;

public class TileEntityAnnouncer extends TileEntity {
    private boolean lastPowered = false;
    private String scriptName = "";
    public String linkKey = "";

    public boolean playLocalSound = false;

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
        this.lastDataReceivedTime = System.currentTimeMillis();
        this.markDirty();

        if (data != null) {
            // Speaker playback is resolved client-side near loaded speakers,
            // so the trigger packet must reach all clients in this dimension.
            NetworkHandler.INSTANCE.sendToDimension(
                new PacketAnnounce(data, this.linkKey, this.playLocalSound, this.xCoord, this.yCoord, this.zCoord),
                this.worldObj.provider.dimensionId
            );
        }
    }

    public void forceStop() {
        if (this.worldObj.isRemote) return;
        NetworkHandler.INSTANCE.sendToDimension(
            new PacketAnnounce(true, this.linkKey),
            this.worldObj.provider.dimensionId
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
        nbt.setBoolean("playLocalSound", this.playLocalSound);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.scriptName = nbt.getString("scriptName");
        this.linkKey = nbt.getString("linkKey");
        this.playLocalSound = nbt.getBoolean("playLocalSound");
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
