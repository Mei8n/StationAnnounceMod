package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.api.AnnounceData;

import java.util.ArrayList;
import java.util.List;

public class PacketAnnounce implements IMessage {
    public String startMelo;
    public List<String> bodySounds;
    public String arrMelo;
    public String linkKey;
    public boolean stopCommand;

    public PacketAnnounce() {}

    public PacketAnnounce(AnnounceData data, String linkKey) {
        this.startMelo = data.startMelo != null ? data.startMelo : "";
        this.bodySounds = data.bodySounds;
        this.arrMelo = data.arrMelo != null ? data.arrMelo : "";
        this.linkKey = linkKey != null ? linkKey : "";
        this.stopCommand = false;
    }

    public PacketAnnounce(boolean stop, String linkKey) {
        this.stopCommand = stop;
        this.linkKey = linkKey != null ? linkKey : "";
        this.bodySounds = new ArrayList<String>();
        this.startMelo = "";
        this.arrMelo = "";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.stopCommand = buf.readBoolean();
        this.linkKey = ByteBufUtils.readUTF8String(buf);

        if (stopCommand) return;

        this.startMelo = ByteBufUtils.readUTF8String(buf);
        this.arrMelo = ByteBufUtils.readUTF8String(buf);

        int size = buf.readInt();
        this.bodySounds = new ArrayList<String>();
        for (int i = 0; i < size; i++) {
            this.bodySounds.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.stopCommand);
        ByteBufUtils.writeUTF8String(buf, this.linkKey != null ? this.linkKey : "");

        if (stopCommand) return;

        ByteBufUtils.writeUTF8String(buf, this.startMelo);
        ByteBufUtils.writeUTF8String(buf, this.arrMelo);

        buf.writeInt(this.bodySounds.size());
        for (String s : this.bodySounds) {
            ByteBufUtils.writeUTF8String(buf, s);
        }
    }
}
