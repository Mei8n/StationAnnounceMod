package jp.me1han.sam;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;

public class MessageAnnounce implements IMessage {
    public String startMelo;
    public List<String> bodySounds;
    public String arrMelo;
    public boolean stopCommand; // 停止命令かどうか

    // Forgeが内部で使用するための空のコンストラクタ
    public MessageAnnounce() {}

    // 放送開始用のコンストラクタ
    public MessageAnnounce(AnnounceData data) {
        this.startMelo = data.startMelo != null ? data.startMelo : "";
        this.bodySounds = data.bodySounds;
        this.arrMelo = data.arrMelo != null ? data.arrMelo : "";
        this.stopCommand = false;
    }

    // 放送停止用のコンストラクタ
    public MessageAnnounce(boolean stop) {
        this.stopCommand = stop;
        this.bodySounds = new ArrayList<>();
        this.startMelo = "";
        this.arrMelo = "";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.stopCommand = buf.readBoolean();
        if (stopCommand) return;

        this.startMelo = ByteBufUtils.readUTF8String(buf);
        this.arrMelo = ByteBufUtils.readUTF8String(buf);

        int size = buf.readInt();
        this.bodySounds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            this.bodySounds.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.stopCommand);
        if (stopCommand) return;

        ByteBufUtils.writeUTF8String(buf, this.startMelo);
        ByteBufUtils.writeUTF8String(buf, this.arrMelo);

        buf.writeInt(this.bodySounds.size());
        for (String s : this.bodySounds) {
            ByteBufUtils.writeUTF8String(buf, s);
        }
    }
}
