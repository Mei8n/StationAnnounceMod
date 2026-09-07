package jp.me1han.sam.render;

import jp.me1han.sam.DepartureSwitchLink;
import jp.me1han.sam.switchmodel.SwitchModelDefinition;
import jp.me1han.sam.switchmodel.SwitchModelRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityDepartureSwitch extends TileEntity {
    public String linkKey = "";
    public String modelName = SwitchModelRegistry.DEFAULT_MODEL;
    private float rotationYaw;
    public float getRotationYaw() { return rotationYaw; }
    public void setRotationYaw(float yaw) {
        rotationYaw = jp.me1han.sam.switchmodel.SwitchYaw.normalize(yaw);
    }
    private boolean activated;
    private boolean controlOn;
    private long pulseUntil;
    public boolean isActivated() { return activated; }
    public boolean isLatched() { return activated && pulseUntil == 0; }
    public boolean isControlOn() { return controlOn; }
    public void setControlOn(boolean on) { controlOn = on; }
    public boolean isMomentary() {
        SwitchModelDefinition model = SwitchModelRegistry.getOrDefault(modelName);
        return model == null || model.switchMode == SwitchModelDefinition.SwitchMode.MOMENTARY;
    }

    public void operate(boolean on, boolean momentary) {
        activated = on;
        pulseUntil = on && momentary ? worldObj.getTotalWorldTime() + 2 : 0;
        SwitchModelDefinition model = SwitchModelRegistry.get(modelName);
        String sound = model == null ? "" : on ? model.soundOn : model.soundOff;
        if (!sound.isEmpty()) worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, sound, 0.5F, 1.0F);
        sync();
    }

    /** Reset is silent: automatic release and emergency stop do not generate click sounds. */
    public void resetState() { controlOn = false; releaseDisplay(); }
    private void releaseDisplay() { activated = false; pulseUntil = 0; sync(); }
    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        if (pulseUntil != 0 && worldObj.getTotalWorldTime() >= pulseUntil) releaseDisplay();
    }
    public void applyConfig(String key, String model, int yaw) {
        TileEntityDepartureMelody old = DepartureSwitchLink.findDevice(this);
        resetState();
        linkKey = TileEntityDepartureMelody.normalize(key);
        modelName = model;
        setRotationYaw(jp.me1han.sam.switchmodel.SwitchYaw.normalize(yaw));
        if (old != null) old.reconcileSwitches();
        sync();
    }
    private void detach() {
        if (worldObj == null || worldObj.isRemote) return;
        TileEntityDepartureMelody old = DepartureSwitchLink.findDevice(this);
        activated = false;
        controlOn = false;
        pulseUntil = 0;
        if (old != null) old.reconcileSwitches();
    }
    @Override public void invalidate() { detach(); super.invalidate(); }
    @Override public void onChunkUnload() { detach(); super.onChunkUnload(); }

    /** Portable configuration deliberately excludes coordinates and live button state. */
    public NBTTagCompound copySettings() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("linkKey", TileEntityDepartureMelody.normalize(linkKey));
        nbt.setString("modelName", modelName);
        nbt.setFloat("RotationYaw", rotationYaw);
        return nbt;
    }
    public void readSettings(NBTTagCompound nbt) {
        linkKey = TileEntityDepartureMelody.normalize(nbt.getString("linkKey"));
        modelName = nbt.hasKey("modelName") ? nbt.getString("modelName") : SwitchModelRegistry.DEFAULT_MODEL;
        setRotationYaw(nbt.getFloat("RotationYaw"));
    }
    private void sync() {
        markDirty();
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", TileEntityDepartureMelody.normalize(linkKey));
        nbt.setString("modelName", modelName);
        nbt.setFloat("RotationYaw", rotationYaw);
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        readSettings(nbt);
        activated = false;
        controlOn = false;
        pulseUntil = 0;
    }
    @Override public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        nbt.setBoolean("Activated", activated);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        readSettings(pkt.func_148857_g());
        activated = pkt.func_148857_g().getBoolean("Activated");
    }
}
