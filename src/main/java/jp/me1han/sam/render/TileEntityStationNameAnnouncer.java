package jp.me1han.sam.render;

import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.api.AnnounceData;
import jp.me1han.sam.api.ScriptType;
import jp.me1han.sam.link.LinkKey;
import jp.me1han.sam.link.SamLinkedTile;
import jp.me1han.sam.link.SamLinkRegistry;
import jp.me1han.sam.network.PacketAnnounce;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/** Shared settings and dispatch for the two station-name trigger blocks. */
public abstract class TileEntityStationNameAnnouncer extends RegisteredTileEntity implements SamLinkedTile {
    private String linkKey = "";
    private String scriptName = "";

    public final boolean triggerStationName() {
        if (worldObj == null || worldObj.isRemote || scriptName.isEmpty()) return false;
        TileEntityAnnouncer parent = SamLinkRegistry.findFirst(worldObj, linkKey, TileEntityAnnouncer.class);
        if (parent == null) return false;
        AnnounceData data = AnnouncePackLoader.runAnnounceScript(scriptName, parent, ScriptType.STATION_NAME);
        if (data == null) return false;
        return parent.startAnnouncement(data, PacketAnnounce.PRIORITY_ANNOUNCE, false) != 0;
    }

    public boolean applyConfig(String key, String script) {
        String normalizedKey = LinkKey.normalize(key);
        String normalizedScript = script == null ? "" : script.trim();
        if (normalizedKey.equals(linkKey) && normalizedScript.equals(scriptName)) return false;
        setLinkKey(normalizedKey);
        scriptName = normalizedScript;
        return true;
    }

    @Override public String getLinkKey() { return linkKey; }
    @Override public void setLinkKey(String key) {
        linkKey = LinkKey.normalize(key);
        SamLinkRegistry.reindex(this);
    }
    public String getScriptName() { return scriptName; }
    public void setScriptName(String value) { scriptName = value == null ? "" : value.trim(); }

    @Override public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("linkKey", linkKey);
        nbt.setString("scriptName", scriptName);
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        setLinkKey(nbt.getString("linkKey"));
        setScriptName(nbt.getString("scriptName"));
    }
    @Override public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound(); writeToNBT(nbt);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net,
            net.minecraft.network.play.server.S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + .5D, yCoord + .5D, zCoord + .5D) <= 64D;
    }
}
