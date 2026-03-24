package jp.me1han.sam;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MessageTrainData implements IMessage {
    public String linkKey, value;

    public MessageTrainData() {}
    public MessageTrainData(String linkKey, String value) {
        this.linkKey = linkKey;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        linkKey = ByteBufUtils.readUTF8String(buf);
        value = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, linkKey);
        ByteBufUtils.writeUTF8String(buf, value);
    }

    public static class Handler implements IMessageHandler<MessageTrainData, IMessage> {
        @Override
        public IMessage onMessage(MessageTrainData msg, MessageContext ctx) {
            // クライアント側でデータを保持（後の放送装置実行時に使用）
            // TrainDataManagerは後ほど作成
            TrainDataManager.INSTANCE.putData(msg.linkKey, msg.value);
            return null;
        }
    }
}
