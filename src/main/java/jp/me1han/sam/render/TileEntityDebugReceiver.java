package jp.me1han.sam.render;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import java.util.Map;

public class TileEntityDebugReceiver extends TileEntity {
    public String linkKey = ""; // 待ち受け用リンクキー

    /**
     * 送信側（選別装置など）からデータが飛んできたときに呼ばれるメソッド
     */
    public void onDataReceived(Map<String, String> data, String sourcePos) {
        if (this.worldObj.isRemote) return;

        // チャットにデバッグ情報を表示
        String header = "§d[SAM-DEBUG] Received from " + sourcePos + " (Key: " + linkKey + ")";
        this.sendMessage(header);

        for (Map.Entry<String, String> entry : data.entrySet()) {
            this.sendMessage("  §7- " + entry.getKey() + " : §f" + entry.getValue());
        }
    }

    private void sendMessage(String text) {
        for (Object obj : this.worldObj.playerEntities) {
            ((net.minecraft.entity.player.EntityPlayer) obj).addChatMessage(new ChatComponentText(text));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (this.linkKey != null) nbt.setString("linkKey", this.linkKey);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.linkKey = nbt.getString("linkKey");
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}
