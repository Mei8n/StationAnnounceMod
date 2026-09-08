package jp.me1han.sam.render;

import jp.me1han.sam.api.AnnounceData;
import jp.me1han.sam.network.PacketAnnounce;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.SpeakerRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class TileEntityAnnouncer extends RegisteredTileEntity {
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
        if (worldObj == null || worldObj.isRemote) return;
        if (scriptName == null || scriptName.isEmpty()) return;

        AnnounceData data = AnnouncePackLoader.runScript(scriptName, this);

        this.receivedData.clear();
        this.lastDataReceivedTime = System.currentTimeMillis();
        this.markDirty();

        if (data != null) sendStart(new PacketAnnounce(data, linkKey, playLocalSound, xCoord, yCoord, zCoord));
    }

    public void startDirectSound(String soundId, int priority, boolean allowOverlap) {
        if (this.worldObj == null || this.worldObj.isRemote || soundId == null || soundId.trim().isEmpty()) {
            return;
        }

        String normalizedSound = soundId.trim();
        AnnounceData data = new AnnounceData("", Collections.singletonList(normalizedSound), "");
        PacketAnnounce packet = new PacketAnnounce(data, linkKey, playLocalSound, xCoord, yCoord, zCoord);
        packet.priority = priority; packet.allowOverlap = allowOverlap;
        sendStart(packet);
    }

    public void notifyDepartureMelodyFinished() {
        if (this.worldObj == null || this.worldObj.isRemote || this.linkKey == null || this.linkKey.trim().isEmpty()) {
            return;
        }

        String normalizedKey = this.linkKey.trim();
        for (Object obj : jp.me1han.sam.LoadedSamTiles.all(this.worldObj)) {
            if (obj instanceof TileEntityAwarenessAnnouncer) {
                TileEntityAwarenessAnnouncer awareness = (TileEntityAwarenessAnnouncer) obj;
                if (normalizedKey.equals(awareness.getNormalizedLinkKey())) {
                    awareness.scheduleAfterDeparture();
                }
            }
        }
    }

    private long departureSessionId;
    public long getDepartureSessionId() { return departureSessionId; }
    public void startDeparture(jp.me1han.sam.api.DepartureProgram program) {
        if (worldObj == null || worldObj.isRemote) return;
        jp.me1han.sam.network.PacketDepartureStart packet = new jp.me1han.sam.network.PacketDepartureStart();
        packet.linkKey = SpeakerRegistry.normalize(linkKey);
        packet.playLocalSound = playLocalSound;
        packet.x = xCoord; packet.y = yCoord; packet.z = zCoord;
        packet.departure = program;
        departureSessionId = sendStart(packet);
    }

    private long sendStart(PacketAnnounce packet) {
        return jp.me1han.sam.network.ServerSessions.start(this, packet);
    }

    @Override public void invalidate() {
        jp.me1han.sam.network.ServerSessions.stopOwner(this);
        super.invalidate();
    }
    @Override public void onChunkUnload() {
        jp.me1han.sam.network.ServerSessions.stopOwner(this);
        super.onChunkUnload();
    }

    public void forceStop() {
        if (this.worldObj.isRemote) return;
        TileEntityDepartureMelody.cancelLinked(this.worldObj, this.linkKey);
        jp.me1han.sam.network.ServerSessions.stopKey(worldObj, linkKey);
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
