package jp.me1han.sam.render;

import jp.me1han.sam.api.AnnounceData;
import jp.me1han.sam.api.ApproachProgram;
import jp.me1han.sam.api.ArrivalPlan;
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
import jp.me1han.sam.link.LinkKey;
import jp.me1han.sam.link.SamLinkedTile;
import jp.me1han.sam.link.SamLinkRegistry;
import jp.me1han.sam.trigger.SamTrigger;
import jp.me1han.sam.trigger.SamTriggerDispatcher;
import jp.me1han.sam.trigger.SamTriggerSourceType;
import jp.me1han.sam.trigger.SamTriggerType;

public class TileEntityAnnouncer extends RegisteredTileEntity implements SamLinkedTile {
    private boolean lastPowered = false;
    private String scriptName = "";
    private String linkKey = "";
    private ArrivalPlan armedArrival;
    private ArrivalPlan pendingArrival;
    private long pendingArrivalDeadline = -1;
    // Only owners with a live reservation; visited on explicit cleanup events, never per tick.
    private static final java.util.Set<TileEntityAnnouncer> ARRIVAL_OWNERS =
        Collections.newSetFromMap(new java.util.IdentityHashMap<TileEntityAnnouncer, Boolean>());

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
        clearArrivalState();
        if (scriptName == null || scriptName.isEmpty()) return;

        ApproachProgram program = AnnouncePackLoader.runApproachScript(scriptName, this);

        this.receivedData.clear();
        this.lastDataReceivedTime = System.currentTimeMillis();
        this.markDirty();

        if (program == null) return;
        long sessionId = sendStart(new PacketAnnounce(program.approach, getLinkKey(), playLocalSound,
            xCoord, yCoord, zCoord));
        if (sessionId != 0 && program.arrival != null) {
            armedArrival = program.arrival.copy();
            ARRIVAL_OWNERS.add(this);
        }
    }

    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || pendingArrival == null) return;
        if (worldObj.getTotalWorldTime() >= pendingArrivalDeadline) playPendingArrival();
    }

    public void startDirectSound(String soundId, int priority, boolean allowOverlap) {
        if (this.worldObj == null || this.worldObj.isRemote || soundId == null || soundId.trim().isEmpty()) {
            return;
        }

        String normalizedSound = soundId.trim();
        AnnounceData data = new AnnounceData("", Collections.singletonList(normalizedSound), "");
        startAnnouncement(data, priority, allowOverlap);
    }

    public void startAnnouncement(AnnounceData data, int priority, boolean allowOverlap) {
        if (this.worldObj == null || this.worldObj.isRemote || data == null) return;
        PacketAnnounce packet = new PacketAnnounce(data, getLinkKey(), playLocalSound, xCoord, yCoord, zCoord);
        packet.priority = priority; packet.allowOverlap = allowOverlap;
        sendStart(packet);
    }

    public void notifyDepartureMelodyFinished() {
        SamTriggerDispatcher.dispatch(this.worldObj, SamTrigger.from(this, SamTriggerType.DEPARTURE_FINISHED,
            this.getLinkKey(), SamTriggerSourceType.INTERNAL, SamTrigger.NO_FORMATION));
    }

    private long departureSessionId;
    public long getDepartureSessionId() { return departureSessionId; }
    public void startDeparture(jp.me1han.sam.api.DepartureProgram program) {
        if (worldObj == null || worldObj.isRemote) return;
        jp.me1han.sam.network.PacketDepartureStart packet = new jp.me1han.sam.network.PacketDepartureStart();
        packet.linkKey = SpeakerRegistry.normalize(getLinkKey());
        packet.playLocalSound = playLocalSound;
        packet.x = xCoord; packet.y = yCoord; packet.z = zCoord;
        packet.departure = program;
        departureSessionId = sendStart(packet);
    }

    private long sendStart(PacketAnnounce packet) {
        return jp.me1han.sam.network.ServerSessions.start(this, packet);
    }

    @Override public void invalidate() {
        clearArrivalState();
        jp.me1han.sam.network.ServerSessions.stopOwner(this);
        super.invalidate();
    }
    @Override public void onChunkUnload() {
        clearArrivalState();
        jp.me1han.sam.network.ServerSessions.stopOwner(this);
        super.onChunkUnload();
    }

    public void forceStop() {
        clearArrivalState();
        stopLinkedPlayback();
    }

    /** Stop Announcer live event: stop first, then arm the already-snapshotted arrival delay. */
    public void onAnnounceStopTrigger() {
        if (worldObj == null || worldObj.isRemote) return;
        ArrivalPlan arrival = armedArrival;
        armedArrival = null;
        stopLinkedPlayback();
        if (arrival == null) return; // A repeated STOP cannot reset an existing pending delay.
        pendingArrival = arrival;
        pendingArrivalDeadline = worldObj.getTotalWorldTime() + arrival.delayTicks;
        if (arrival.delayTicks == 0) playPendingArrival();
    }

    private void stopLinkedPlayback() {
        if (worldObj == null || worldObj.isRemote) return;
        TileEntityDepartureMelody.cancelLinked(this.worldObj, this.getLinkKey());
        jp.me1han.sam.network.ServerSessions.stopKey(worldObj, getLinkKey());
    }

    private void playPendingArrival() {
        ArrivalPlan arrival = pendingArrival;
        clearArrivalState();
        if (arrival != null)
            startAnnouncement(arrival.announcement, PacketAnnounce.PRIORITY_ANNOUNCE, false);
    }

    private void clearArrivalState() {
        armedArrival = null;
        pendingArrival = null;
        pendingArrivalDeadline = -1;
        if (worldObj != null && !worldObj.isRemote) ARRIVAL_OWNERS.remove(this);
    }

    public boolean hasArmedArrival() { return armedArrival != null; }
    public boolean hasPendingArrival() { return pendingArrival != null; }
    public int getPendingArrivalTicks() {
        return pendingArrival == null ? -1 : (int)Math.max(0, pendingArrivalDeadline - worldObj.getTotalWorldTime());
    }

    /** World/server shutdown and global stop also cancel plans whose approach session has ended. */
    public static void clearArrivals(net.minecraft.world.World world) {
        java.util.Iterator<TileEntityAnnouncer> owners = ARRIVAL_OWNERS.iterator();
        while (owners.hasNext()) {
            TileEntityAnnouncer owner = owners.next();
            if (world == null || owner.worldObj == world) {
                owner.armedArrival = null;
                owner.pendingArrival = null;
                owner.pendingArrivalDeadline = -1;
                owners.remove();
            }
        }
    }

    public void onDataReceived(Map<String, String> data, String sourcePos) {
        if (this.worldObj.isRemote) return;

        this.receivedData = new HashMap<String, String>(data);
        this.lastDataReceivedTime = System.currentTimeMillis();
    }

    public String getScriptName() { return this.scriptName; }
    public void setScriptName(String name) {
        clearArrivalState();
        this.scriptName = name;
    }

    @Override public String getLinkKey() { return this.linkKey; }
    @Override public void setLinkKey(String key) {
        if (!this.linkKey.equals(LinkKey.normalize(key))) clearArrivalState();
        this.linkKey = LinkKey.normalize(key);
        SamLinkRegistry.reindex(this);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (this.scriptName != null) nbt.setString("scriptName", this.scriptName);
        nbt.setString("linkKey", this.getLinkKey());
        nbt.setBoolean("playLocalSound", this.playLocalSound);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        clearArrivalState();
        super.readFromNBT(nbt);
        this.scriptName = nbt.getString("scriptName");
        this.setLinkKey(nbt.getString("linkKey"));
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
