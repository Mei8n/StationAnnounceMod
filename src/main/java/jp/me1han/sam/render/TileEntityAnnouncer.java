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

public class TileEntityAnnouncer extends TileEntity {
    private static class SpeakerCollectResult {
        final List<PacketAnnounce.SpeakerData> speakers = new ArrayList<PacketAnnounce.SpeakerData>();
        int totalSpeakers = 0;
        String sampleKeys = "";
    }

    private boolean lastPowered = false;
    private String scriptName = "";
    public String linkKey = "";

    public boolean playLocalSound = false;

    public Map<String, String> receivedData = new HashMap<String, String>();
    public long lastDataReceivedTime = 0;

    @Override
    public void updateEntity() {
        // TileEntityがloadedTileEntityListに登録されるために必須
        // サーバー側でのみ実行
        if (this.worldObj != null && !this.worldObj.isRemote) {
            // 特に処理は不要
        }
    }

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
            SpeakerCollectResult scanResult = collectSpeakersByKey(this.linkKey);
            // Speaker playback is resolved client-side near loaded speakers,
            // so the trigger packet must reach all clients in this dimension.
            NetworkHandler.INSTANCE.sendToDimension(
                new PacketAnnounce(data, this.linkKey, this.playLocalSound, this.xCoord, this.yCoord, this.zCoord,
                    scanResult.speakers, scanResult.totalSpeakers, scanResult.sampleKeys),
                this.worldObj.provider.dimensionId
            );
        }
    }

    private SpeakerCollectResult collectSpeakersByKey(String key) {
        SpeakerCollectResult result = new SpeakerCollectResult();
        if (this.worldObj == null || this.worldObj.isRemote) {
            return result;
        }

        String normalizedKey = key == null ? "" : key.trim();
        if (normalizedKey.isEmpty()) {
            return result;
        }

        int loadedSpeakerCount = 0;
        StringBuilder sampleKeys = new StringBuilder();

        List<PacketAnnounce.SpeakerData> registered = SpeakerRegistry.findByKey(this.worldObj.provider.dimensionId, normalizedKey);
        for (PacketAnnounce.SpeakerData speaker : registered) {
            if (speaker == null) {
                continue;
            }
            result.speakers.add(speaker);
        }
        result.totalSpeakers = SpeakerRegistry.countByDimension(this.worldObj.provider.dimensionId);
        String registeredSample = SpeakerRegistry.sampleKeys(this.worldObj.provider.dimensionId, 8);
        if (!registeredSample.isEmpty()) {
            sampleKeys.append(registeredSample);
        }

        for (Object obj : this.worldObj.loadedTileEntityList) {
            if (!(obj instanceof TileEntitySpeaker)) {
                continue;
            }

            TileEntitySpeaker speaker = (TileEntitySpeaker) obj;
            loadedSpeakerCount++;
            String speakerKey = speaker.linkKey == null ? "" : speaker.linkKey.trim();
            if (!speakerKey.isEmpty() && sampleKeys.length() < 64) {
                if (sampleKeys.length() > 0) {
                    sampleKeys.append(",");
                }
                sampleKeys.append(speakerKey);
            }
            if (!speakerKey.isEmpty() && normalizedKey.equals(speakerKey) && !containsSpeaker(result.speakers, speaker.xCoord, speaker.yCoord, speaker.zCoord)) {
                result.speakers.add(new PacketAnnounce.SpeakerData(
                    speaker.xCoord,
                    speaker.yCoord,
                    speaker.zCoord,
                    speaker.range,
                    speaker.volume
                ));
            }
        }

        if (loadedSpeakerCount > result.totalSpeakers) {
            result.totalSpeakers = loadedSpeakerCount;
        }

        result.sampleKeys = sampleKeys.toString();

        NetworkHandler.sendDebugMessage(
            this.worldObj,
            normalizedKey,
            "[SAM-SPEAKER] SERVER_SCAN key=" + normalizedKey
                + " loadedTE=" + this.worldObj.loadedTileEntityList.size()
                + " speakers=" + result.totalSpeakers
                + " matched=" + result.speakers.size()
                + " sampleKeys=" + (sampleKeys.length() == 0 ? "none" : sampleKeys.toString())
        );

        return result;
    }

    private boolean containsSpeaker(List<PacketAnnounce.SpeakerData> speakers, int x, int y, int z) {
        for (PacketAnnounce.SpeakerData speaker : speakers) {
            if (speaker != null && speaker.x == x && speaker.y == y && speaker.z == z) {
                return true;
            }
        }
        return false;
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
