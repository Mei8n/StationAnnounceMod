package jp.me1han.sam.render;

import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.api.DepartureProgram;
import jp.me1han.sam.api.DepartureSequence;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.network.PacketDepartureControl;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class TileEntityDepartureMelody extends RegisteredTileEntity {
    public String linkKey = "";
    /** Retained solely to migrate previously placed, one-shot devices. */
    public String soundId = "";
    public String scriptName = "";
    public String lastError = "";
    private boolean lastPowered;
    private boolean poweredInitialized;
    private long sessionId;
    private DepartureProgram program;
    private DepartureSequence sequence;
    private TileEntityAnnouncer activeParent;
    private boolean redstoneOn;
    /** Server-runtime logical ON sources; deliberately not persisted or synchronized. */
    private final Map<Long, TileEntityDepartureSwitch> activeSwitches = new HashMap<>();
    private boolean resettingSwitches;
    private boolean syncedOn;
    private long phaseStartedTick = Long.MIN_VALUE;
    private long releasedTick = Long.MIN_VALUE;

    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        // Read the current level on load without replaying an old rising edge.
        if (!poweredInitialized) {
            lastPowered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
            poweredInitialized = true;
        }
        if (sequence == null) return;
        if (activeParent == null || activeParent.isInvalid()
            || !worldObj.blockExists(activeParent.xCoord, activeParent.yCoord, activeParent.zCoord)
            || worldObj.getTileEntity(activeParent.xCoord, activeParent.yCoord, activeParent.zCoord) != activeParent
            || !normalize(linkKey).equals(normalize(activeParent.linkKey))) {
            cancelPlayback();
            return;
        }
        if (worldObj.getTotalWorldTime() != phaseStartedTick)
            sequence.tick(worldObj.getTotalWorldTime() == releasedTick);
        if (sequence.isFinished()) {
            sequence = null;
            activeParent = null;
            redstoneOn = false;
            activeSwitches.clear();
            sync();
        }
    }

    public boolean isOn() { return worldObj != null && worldObj.isRemote ? syncedOn : sequence != null && sequence.isOn(); }
    public boolean isPlaying() { return sequence != null && !sequence.isFinished(); }

    public void onRedstoneUpdate(boolean powered) {
        if (worldObj == null || worldObj.isRemote) return;
        if (powered == lastPowered) return;
        lastPowered = powered;
        poweredInitialized = true;
        if (powered) operate(null, true);
        else { redstoneOn = false; updateControlState(); }
    }

    public void startMelody() { operate(null, true); }

    /** Called once on the logical server by an independent linked SAM switch. */
    public boolean click(TileEntity source) { return operate(source, false); }

    private boolean operate(TileEntity source, boolean redstone) {
        if (worldObj == null || worldObj.isRemote) return false;
        try {
            TileEntityAnnouncer parent = findParent();
            if (parent == null) throw new IllegalStateException("Exactly one loaded parent announcer must match the link key");
            for (Object obj : jp.me1han.sam.LoadedSamTiles.all(worldObj)) {
                if (obj != this && obj instanceof TileEntityDepartureMelody && !((TileEntity) obj).isInvalid()
                    && normalize(linkKey).equals(normalize(((TileEntityDepartureMelody) obj).linkKey))) {
                    throw new IllegalStateException("Only one melody device may use a link key");
                }
            }
            DepartureProgram candidate = isPlaying() ? program : loadProgram();
            TileEntityDepartureSwitch button = source instanceof TileEntityDepartureSwitch ? (TileEntityDepartureSwitch) source : null;
            boolean momentary = button == null || button.isMomentary();
            boolean physicalOn = momentary || !button.isActivated();
            boolean wasOn = button == null ? redstoneOn : button.isControlOn();
            if (button != null) button.operate(physicalOn, momentary);

            if (candidate.alternate) {
                if (wasOn) {
                    if (button != null) {
                        button.setControlOn(false, this);
                    } else {
                        redstoneOn = false;
                        updateControlState();
                    }
                } else {
                    if (button != null) {
                        button.setControlOn(true, this);
                    } else redstoneOn = true;
                    if (!isOn()) begin(parent, candidate);
                }
            } else if (physicalOn && !isPlaying()) {
                begin(parent, candidate);
            }
            lastError = "";
            sync();
            return true;
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            StationAnnounceModCore.logger.error("[SAM] Departure " + xCoord + "," + yCoord + "," + zCoord + ": " + lastError, e);
            sync();
            return false;
        }
    }

    private DepartureProgram loadProgram() throws Exception {
        if (!normalize(scriptName).isEmpty()) return AnnouncePackLoader.runDepartureScript(scriptName, this);
        DepartureProgram legacy = new DepartureProgram(false);
        legacy.melody = normalize(soundId);
        return legacy.resolve(AnnouncePackLoader.soundTicks);
    }

    private void begin(final TileEntityAnnouncer parent, DepartureProgram selected) {
        stopSequence();
        program = selected;
        activeParent = parent;
        phaseStartedTick = worldObj.getTotalWorldTime();
        parent.startDeparture(program);
        sessionId = parent.getDepartureSessionId();
        sequence = new DepartureSequence(program, new DepartureSequence.Output() {
            public void play(DepartureSequence.Channel channel, String sound) { }
            public void stop(DepartureSequence.Channel channel) { }
            public void finished() { parent.notifyDepartureMelodyFinished(); }
        });
    }

    /** Event-driven update from a linked switch's logical control state. */
    public void setSwitchControl(TileEntityDepartureSwitch button, boolean on) {
        if (worldObj == null || worldObj.isRemote || button == null || button.getWorldObj() != worldObj) return;
        long position = jp.me1han.sam.SpeakerRegistry.position(button.xCoord, button.yCoord, button.zCoord);
        boolean changed;
        if (on) {
            if (!normalize(linkKey).equals(normalize(button.linkKey))) return;
            changed = activeSwitches.put(position, button) != button;
        } else {
            if (activeSwitches.get(position) != button) return;
            activeSwitches.remove(position);
            changed = true;
        }
        if (changed && !resettingSwitches) updateControlState();
    }

    private void updateControlState() {
        if (worldObj == null || worldObj.isRemote || program == null || !program.alternate || !isOn()) return;
        if (!redstoneOn && activeSwitches.isEmpty()) release();
    }

    /** Runtime diagnostic used by the headless state-transition tests. */
    public int getActiveSwitchCount() { return activeSwitches.size(); }

    private void release() {
        if (sequence == null || !sequence.isOn()) return;
        sequence.release();
        releasedTick = worldObj.getTotalWorldTime();
        sendControl(false);
        sync();
    }

    public void cancelPlayback() {
        redstoneOn = false;
        if (worldObj != null && !worldObj.isRemote) {
            resettingSwitches = true;
            try {
                for (Object obj : jp.me1han.sam.LoadedSamTiles.all(worldObj)) {
                    if (obj instanceof TileEntityDepartureSwitch
                        && normalize(linkKey).equals(normalize(((TileEntityDepartureSwitch) obj).linkKey))) {
                        ((TileEntityDepartureSwitch) obj).resetState(this);
                    }
                }
            } finally {
                activeSwitches.clear();
                resettingSwitches = false;
            }
        } else {
            activeSwitches.clear();
        }
        cancelSequence();
    }

    private void cancelSequence() {
        redstoneOn = false;
        activeSwitches.clear();
        stopSequence();
    }

    /** Replacing an OFF tail with a new ON sequence must retain the current input sources. */
    private void stopSequence() {
        if (sequence == null) return;
        sequence.cancel();
        sequence = null;
        activeParent = null;
        if (worldObj != null && !worldObj.isRemote) {
            sendControl(true);
            sync();
        }
    }

    protected void sendControl(boolean cancel) {
        jp.me1han.sam.network.ServerSessions.control(sessionId, cancel);
        if (cancel) sessionId = 0;
    }

    public static void cancelLinked(World world, String key) {
        String normalized = normalize(key);
        for (Object obj : jp.me1han.sam.LoadedSamTiles.all(world)) {
            if (obj instanceof TileEntityDepartureMelody) {
                TileEntityDepartureMelody tile = (TileEntityDepartureMelody) obj;
                if (normalized.isEmpty() || normalized.equals(normalize(tile.linkKey))) tile.cancelPlayback();
            }
        }
    }

    public void applyConfig(String key, String legacySound, String script) {
        if (normalize(key).equals(linkKey) && normalize(legacySound).equals(soundId) && normalize(script).equals(scriptName)) return;
        cancelPlayback();
        linkKey = normalize(key);
        soundId = normalize(legacySound);
        scriptName = normalize(script);
        lastError = "";
        sync();
    }

    private TileEntityAnnouncer findParent() {
        String key = normalize(linkKey);
        if (worldObj == null || key.isEmpty()) return null;
        TileEntityAnnouncer found = null;
        for (Object obj : jp.me1han.sam.LoadedSamTiles.all(worldObj)) {
            if (obj instanceof TileEntityAnnouncer && !((TileEntity) obj).isInvalid()) {
                TileEntityAnnouncer parent = (TileEntityAnnouncer) obj;
                if (key.equals(normalize(parent.linkKey))) {
                    if (found != null) return null;
                    found = parent;
                }
            }
        }
        return found;
    }

    private void sync() {
        markDirty();
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public static String normalize(String value) { return value == null ? "" : value.trim(); }
    @Override public void invalidate() { cancelPlayback(); super.invalidate(); }
    @Override public void onChunkUnload() { cancelPlayback(); super.onChunkUnload(); }

    @Override public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", normalize(linkKey));
        nbt.setString("soundId", normalize(soundId));
        nbt.setString("scriptName", normalize(scriptName));
        // Deliberately do not persist live playback across world/chunk reloads.
    }

    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        linkKey = nbt.getString("linkKey");
        soundId = nbt.getString("soundId");
        scriptName = nbt.hasKey("scriptName") ? nbt.getString("scriptName") : "";
        syncedOn = nbt.getBoolean("departureOn");
        lastError = nbt.getString("departureError");
    }

    @Override public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        nbt.setBoolean("departureOn", isOn());
        nbt.setString("departureError", lastError);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }
}
