package jp.me1han.sam.render;

import jp.me1han.sam.SpeakerRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileEntitySpeaker extends TileEntity {
    public static final float MAX_OFFSET = 16.0F;
    public String linkKey = "";
    public int range = 16;
    public float volume = 1.0f;
    public String modelName = "";
    private float rotationYaw;
    private float offsetX, offsetY, offsetZ;
    private volatile boolean clientConfigSynced;

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", this.linkKey != null ? this.linkKey : "");
        nbt.setInteger("range", this.range);
        nbt.setFloat("volume", this.volume);
        nbt.setString("modelName", modelName == null ? "" : modelName);
        nbt.setFloat("RotationYaw", rotationYaw);
        nbt.setFloat("offsetX", offsetX); nbt.setFloat("offsetY", offsetY); nbt.setFloat("offsetZ", offsetZ);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        String key = nbt.getString("linkKey");
        this.linkKey = jp.me1han.sam.link.LinkKey.normalize(key);
        this.range = nbt.hasKey("range") ? nbt.getInteger("range") : 16;
        this.volume = nbt.hasKey("volume") ? nbt.getFloat("volume") : 1.0f;
        this.modelName = nbt.hasKey("modelName") ? nbt.getString("modelName").trim() : "";
        if (!jp.me1han.sam.network.PacketLimits.string(this.modelName, jp.me1han.sam.network.PacketLimits.MODEL)) this.modelName = "";
        float yaw = nbt.hasKey("RotationYaw") ? nbt.getFloat("RotationYaw") : 0;
        this.rotationYaw = finite(yaw) ? jp.me1han.sam.switchmodel.SwitchYaw.normalize(yaw) : 0;
        this.offsetX = safeOffset(nbt.getFloat("offsetX"));
        this.offsetY = safeOffset(nbt.getFloat("offsetY"));
        this.offsetZ = safeOffset(nbt.getFloat("offsetZ"));

        this.range = Math.max(1, Math.min(jp.me1han.sam.network.PacketLimits.MAX_RANGE, this.range));
        this.volume = Float.isNaN(this.volume) || Float.isInfinite(this.volume) ? 1.0F
            : Math.max(0, Math.min(jp.me1han.sam.network.PacketLimits.MAX_VOLUME, this.volume));
        syncRegistry();
    }

    @Override
    public boolean canUpdate() { return false; }

    @Override
    public void validate() {
        super.validate();
        syncRegistry();
    }

    @Override
    public void invalidate() {
        removeFromRegistry();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        removeFromRegistry();
        super.onChunkUnload();
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
        this.clientConfigSynced = true;
        jp.me1han.sam.client.ClientSpeakerRegistry.register(this);
    }

    /** True only after this client TE has applied a server description packet. */
    public boolean isClientConfigSynced() {
        return this.clientConfigSynced;
    }

    public boolean applyConfig(String key, int range, float volume) {
        return applyConfig(key, range, volume, modelName, rotationYaw, offsetX, offsetY, offsetZ);
    }

    public boolean applyConfig(String key, int range, float volume, String model, float yaw,
                               float x, float y, float z) {
        key = SpeakerRegistry.normalize(key);
        model = model == null ? "" : model.trim();
        if (!jp.me1han.sam.network.PacketLimits.string(key, jp.me1han.sam.network.PacketLimits.LINK_KEY)
            || !jp.me1han.sam.network.PacketLimits.speaker(range, volume)
            || !jp.me1han.sam.network.PacketLimits.string(model, jp.me1han.sam.network.PacketLimits.MODEL)
            || !finite(yaw) || !validOffset(x) || !validOffset(y) || !validOffset(z)) return false;
        float normalizedYaw = jp.me1han.sam.switchmodel.SwitchYaw.normalize(yaw);
        boolean routingChanged = !key.equals(linkKey) || this.range != range || this.volume != volume;
        boolean visualChanged = !model.equals(modelName) || rotationYaw != normalizedYaw
            || offsetX != x || offsetY != y || offsetZ != z;
        if (!routingChanged && !visualChanged) return false;
        linkKey = key; this.range = range; this.volume = volume; modelName = model;
        rotationYaw = normalizedYaw; offsetX = x; offsetY = y; offsetZ = z;
        if (routingChanged) syncRegistry();
        markDirty();
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return true;
    }

    public float getRotationYaw() { return rotationYaw; }
    public void setRotationYaw(float yaw) { rotationYaw = jp.me1han.sam.switchmodel.SwitchYaw.normalize(yaw); }
    public float getOffsetX() { return offsetX; }
    public float getOffsetY() { return offsetY; }
    public float getOffsetZ() { return offsetZ; }
    public static boolean validOffset(float value) { return finite(value) && Math.abs(value) <= MAX_OFFSET; }
    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
    private static float safeOffset(float value) { return validOffset(value) ? value : 0; }

    public jp.me1han.sam.speakermodel.SpeakerModelDefinition getModelDefinition() {
        return modelName == null || modelName.isEmpty() ? null
            : jp.me1han.sam.speakermodel.SpeakerModelRegistry.get(modelName);
    }

    @Override public net.minecraft.util.AxisAlignedBB getRenderBoundingBox() {
        jp.me1han.sam.speakermodel.SpeakerModelDefinition model = getModelDefinition();
        if (model == null) return net.minecraft.util.AxisAlignedBB.getBoundingBox(
            xCoord, yCoord, zCoord, xCoord + 1, yCoord + 1, zCoord + 1);
        double[] b = jp.me1han.sam.switchmodel.SwitchYaw.rotateBounds(model.bounds, rotationYaw);
        return net.minecraft.util.AxisAlignedBB.getBoundingBox(
            xCoord + b[0] + offsetX, yCoord + b[1] + offsetY, zCoord + b[2] + offsetZ,
            xCoord + b[3] + offsetX, yCoord + b[4] + offsetY, zCoord + b[5] + offsetZ);
    }

    private void syncRegistry() {
        if (this.worldObj == null) return;
        if (this.worldObj.isRemote) jp.me1han.sam.client.ClientSpeakerRegistry.register(this);
        else SpeakerRegistry.register(this);
    }

    private void removeFromRegistry() {
        if (this.worldObj == null) return;
        if (this.worldObj.isRemote) jp.me1han.sam.client.ClientSpeakerRegistry.unregister(this);
        else SpeakerRegistry.unregister(this);
    }
}
