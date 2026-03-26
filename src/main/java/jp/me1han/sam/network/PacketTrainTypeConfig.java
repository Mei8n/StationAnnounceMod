package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.api.TrainTypeCondition;

import java.util.ArrayList;
import java.util.List;

public class PacketTrainTypeConfig implements IMessage {
    public int x, y, z;
    public List<TrainTypeCondition> conditions;
    public String linkKey;
    public boolean isControlCar; // 変数名を変更

    public PacketTrainTypeConfig() {
        this.conditions = new ArrayList<TrainTypeCondition>();
    }

    public PacketTrainTypeConfig(int x, int y, int z, List<TrainTypeCondition> conditions, String linkKey, boolean isControlCar) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.conditions = conditions;
        this.linkKey = linkKey;
        this.isControlCar = isControlCar;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();

        int size = buf.readInt();
        this.conditions = new ArrayList<TrainTypeCondition>();
        for (int i = 0; i < size; i++) {
            String key = ByteBufUtils.readUTF8String(buf);
            int type = buf.readInt();
            this.conditions.add(new TrainTypeCondition(key, type));
        }

        this.linkKey = ByteBufUtils.readUTF8String(buf);
        this.isControlCar = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);

        buf.writeInt(this.conditions.size());
        for (TrainTypeCondition cond : this.conditions) {
            ByteBufUtils.writeUTF8String(buf, cond.key);
            buf.writeInt(cond.type);
        }

        ByteBufUtils.writeUTF8String(buf, this.linkKey);
        buf.writeBoolean(this.isControlCar);
    }
}
