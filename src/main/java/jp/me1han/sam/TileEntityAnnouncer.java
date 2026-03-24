package jp.me1han.sam;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import cpw.mods.fml.common.network.NetworkRegistry;

public class TileEntityAnnouncer extends TileEntity {
    private boolean lastPowered = false;
    private String scriptName = "";

    public void onRedstoneUpdate(boolean powered) {
        if (this.worldObj.isRemote) return;

        // 立ち上がり（OFF -> ON）の時だけ放送を開始する
        if (powered && !lastPowered) {
            startAnnounce();
        }

        // 【修正】powered == false 時の stopAnnounce() を削除。
        // これによりパルス信号が入った後、信号が消えても放送は継続されます。

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

    // 別ブロック（停止用ボタンなど）から呼び出すためのメソッド
    public void forceStop() {
        if (this.worldObj.isRemote) return;
        NetworkHandler.INSTANCE.sendToAllAround(
            new MessageAnnounce(true),
            new NetworkRegistry.TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 64)
        );
    }

    public String getScriptName() { return this.scriptName; }
    public void setScriptName(String name) { this.scriptName = name; }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.scriptName = nbt.getString("scriptName");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("scriptName", this.scriptName);
    }
}
