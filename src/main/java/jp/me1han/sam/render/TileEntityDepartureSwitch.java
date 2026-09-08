package jp.me1han.sam.render;

import jp.me1han.sam.DepartureSwitchLink;
import jp.me1han.sam.switchmodel.SwitchModelDefinition;
import jp.me1han.sam.switchmodel.SwitchModelRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityDepartureSwitch extends RegisteredTileEntity {
    public String linkKey = "";
    public String modelName = SwitchModelRegistry.DEFAULT_MODEL;
    private float rotationYaw;
    private float offsetX, offsetY, offsetZ;
    public float getRotationYaw() { return rotationYaw; }
    public void setRotationYaw(float yaw) {
        rotationYaw = jp.me1han.sam.switchmodel.SwitchYaw.normalize(yaw);
    }
    public float getOffsetX() { return offsetX; }
    public float getOffsetY() { return offsetY; }
    public float getOffsetZ() { return offsetZ; }
    public static boolean validOffset(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
    public void setOffset(float x, float y, float z) {
        if (!validOffset(x) || !validOffset(y) || !validOffset(z)) throw new IllegalArgumentException("Invalid switch offset");
        offsetX = x; offsetY = y; offsetZ = z;
    }
    private boolean activated;
    private boolean controlOn;
    /** Server-runtime owner of the logical ON registration; never persisted or synchronized. */
    private TileEntityDepartureMelody controlDevice;
    private long pulseUntil;
    public boolean isActivated() { return activated; }
    public boolean isLatched() { return activated && pulseUntil == 0; }
    public boolean isControlOn() { return controlOn; }
    public void setControlOn(boolean on) {
        setControlOn(on, on ? DepartureSwitchLink.findDevice(this) : null);
    }
    void setControlOn(boolean on, TileEntityDepartureMelody device) {
        if (on) {
            if (controlOn) {
                if (controlDevice == null && device != null) {
                    controlDevice = device;
                    controlDevice.setSwitchControl(this, true);
                }
                return;
            }
            controlOn = true;
            controlDevice = device;
            if (controlDevice != null) controlDevice.setSwitchControl(this, true);
        } else {
            TileEntityDepartureMelody owner = controlDevice != null ? controlDevice : device;
            if (!controlOn && owner == null) return;
            controlOn = false;
            if (owner != null) owner.setSwitchControl(this, false);
            controlDevice = null;
        }
    }
    protected final TileEntityDepartureMelody getControlDevice() { return controlDevice; }
    public boolean isMomentary() {
        SwitchModelDefinition model = SwitchModelRegistry.getOrDefault(modelName);
        return model == null || model.switchMode == SwitchModelDefinition.SwitchMode.MOMENTARY;
    }

    public void operate(boolean on, boolean momentary) {
        activated = on;
        pulseUntil = on && momentary ? worldObj.getTotalWorldTime() + 2 : 0;
        SwitchModelDefinition model = SwitchModelRegistry.get(modelName);
        String sound = model == null ? "" : on ? model.soundOn : model.soundOff;
        if (!sound.isEmpty()) worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5,
            zCoord + 0.5, sound, 0.5F, 1.0F);
        sync();
    }

    /** Reset is silent: automatic release and emergency stop do not generate click sounds. */
    public void resetState() { resetState(null); }
    void resetState(TileEntityDepartureMelody device) { setControlOn(false, device); releaseDisplay(); }
    private void releaseDisplay() { activated = false; pulseUntil = 0; sync(); }
    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        if (pulseUntil != 0 && worldObj.getTotalWorldTime() >= pulseUntil) releaseDisplay();
    }
    public void applyConfig(String key, String model, int yaw) {
        applyConfig(key, model, yaw, offsetX, offsetY, offsetZ);
    }
    public void applyConfig(String key, String model, int yaw, float x, float y, float z) {
        resetState();
        linkKey = TileEntityDepartureMelody.normalize(key);
        modelName = model;
        setRotationYaw(jp.me1han.sam.switchmodel.SwitchYaw.normalize(yaw));
        setOffset(x, y, z);
        sync();
    }
    private void detach() {
        if (worldObj == null || worldObj.isRemote) return;
        resetState();
    }
    @Override public void invalidate() { detach(); super.invalidate(); }
    @Override public void onChunkUnload() { detach(); super.onChunkUnload(); }

    /** Portable configuration deliberately excludes coordinates and live button state. */
    public NBTTagCompound copySettings() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("linkKey", TileEntityDepartureMelody.normalize(linkKey));
        nbt.setString("modelName", modelName);
        nbt.setFloat("RotationYaw", rotationYaw);
        nbt.setFloat("offsetX", offsetX);
        nbt.setFloat("offsetY", offsetY);
        nbt.setFloat("offsetZ", offsetZ);
        return nbt;
    }
    public void readSettings(NBTTagCompound nbt) {
        linkKey = TileEntityDepartureMelody.normalize(nbt.getString("linkKey"));
        modelName = nbt.hasKey("modelName") ? nbt.getString("modelName") : SwitchModelRegistry.DEFAULT_MODEL;
        setRotationYaw(nbt.getFloat("RotationYaw"));
        float x = nbt.getFloat("offsetX"), y = nbt.getFloat("offsetY"), z = nbt.getFloat("offsetZ");
        setOffset(validOffset(x) ? x : 0, validOffset(y) ? y : 0, validOffset(z) ? z : 0);
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
        nbt.setFloat("offsetX", offsetX);
        nbt.setFloat("offsetY", offsetY);
        nbt.setFloat("offsetZ", offsetZ);
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        readSettings(nbt);
        activated = false;
        controlOn = false;
        controlDevice = null;
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
    @Override public net.minecraft.util.AxisAlignedBB getRenderBoundingBox() {
        SwitchModelDefinition model = SwitchModelRegistry.getOrDefault(modelName);
        double[] source = model == null ? new double[]{0.25, 0, 0.25, 0.75, 0.3, 0.75} : model.bounds;
        double[] b = jp.me1han.sam.switchmodel.SwitchYaw.rotateBounds(source, rotationYaw);
        return net.minecraft.util.AxisAlignedBB.getBoundingBox(
            xCoord + b[0] + offsetX, yCoord + b[1] + offsetY, zCoord + b[2] + offsetZ,
            xCoord + b[3] + offsetX, yCoord + b[4] + offsetY, zCoord + b[5] + offsetZ);
    }
}
