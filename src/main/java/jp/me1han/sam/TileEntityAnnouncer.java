package jp.me1han.sam;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import cpw.mods.fml.common.network.NetworkRegistry;

public class TileEntityAnnouncer extends TileEntity {
    private boolean lastPowered = false;
    private String scriptName = "";
    private String linkKey = ""; // 追加

    public void onRedstoneUpdate(boolean powered) {
        if (this.worldObj.isRemote) return;
        if (powered && !lastPowered) {
            startAnnounce();
        }
        this.lastPowered = powered;
    }

    private void startAnnounce() {
        if (scriptName == null || scriptName.isEmpty()) return;

        AnnounceData data = PackLoader.runScript(scriptName, this);
        if (data != null) {
            NetworkHandler.INSTANCE.sendToAllAround(
                new MessageAnnounce(data),
                new NetworkRegistry.TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 64)
            );
        }
    }

    public String getScriptName() { return this.scriptName; }
    public void setScriptName(String name) { this.scriptName = name; }

    // リンクキーのGetter/Setter
    public String getLinkKey() { return this.linkKey; }
    public void setLinkKey(String key) { this.linkKey = key; }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.scriptName = nbt.getString("scriptName");
        this.linkKey = nbt.getString("linkKey"); // 読込
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("scriptName", this.scriptName);
        nbt.setString("linkKey", this.linkKey); // 保存
    }
}
