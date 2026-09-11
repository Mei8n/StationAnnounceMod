package jp.me1han.sam.network;

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

        int size = PacketLimits.readCount(buf, PacketLimits.CONDITIONS);
        this.conditions = new ArrayList<TrainTypeCondition>();
        for (int i = 0; i < size; i++) {
            String key = PacketLimits.readString(buf, PacketLimits.NAME);
            int type = buf.readInt();
            this.conditions.add(new TrainTypeCondition(key, type));
        }

        this.linkKey = PacketLimits.readString(buf, PacketLimits.LINK_KEY);
        this.isControlCar = buf.readBoolean();
        PacketLimits.requireDecoded(isValidPayload(), "Invalid train type config payload");
    }

    public boolean isValidPayload() {
        if (!ConfigAccess.key(PacketLimits.normalize(linkKey)) || conditions == null
            || conditions.size() > PacketLimits.CONDITIONS) return false;
        for (TrainTypeCondition condition : conditions) {
            if (condition == null || !PacketLimits.string(PacketLimits.normalize(condition.key), PacketLimits.NAME)
                || condition.type < 0 || condition.type > 3) return false;
        }
        return true;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketLimits.require(isValidPayload(), "Invalid train type config payload");
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);

        PacketLimits.writeCount(buf, this.conditions.size(), PacketLimits.CONDITIONS);
        for (TrainTypeCondition cond : this.conditions) {
            PacketLimits.writeString(buf, cond.key, PacketLimits.NAME);
            buf.writeInt(cond.type);
        }

        PacketLimits.writeString(buf, this.linkKey, PacketLimits.LINK_KEY);
        buf.writeBoolean(this.isControlCar);
    }
}
