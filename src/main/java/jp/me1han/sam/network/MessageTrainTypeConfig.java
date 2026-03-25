package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.api.TrainTypeCondition;

import java.util.ArrayList;
import java.util.List;

public class MessageTrainTypeConfig implements IMessage {
    public int x, y, z;
    public List<TrainTypeCondition> conditions;

    public MessageTrainTypeConfig() {
        this.conditions = new ArrayList<TrainTypeCondition>();
    }

    public MessageTrainTypeConfig(int x, int y, int z, List<TrainTypeCondition> conditions) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.conditions = conditions;
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
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(conditions.size());
        for (TrainTypeCondition cond : conditions) {
            ByteBufUtils.writeUTF8String(buf, cond.key);
            buf.writeInt(cond.type);
        }
    }
}
