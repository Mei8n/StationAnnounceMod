package jp.me1han.sam;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.tileentity.TileEntity;

public class NetworkHandler {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("SAM_CHANNEL");

    public static void init() {
        // ID 0: サーバー -> クライアント (再生命令)
        INSTANCE.registerMessage(AnnounceHandler.class, MessageAnnounce.class, 0, Side.CLIENT);
        // ID 1: クライアント -> サーバー (設定保存)
        INSTANCE.registerMessage(ConfigHandler.class, MessageConfig.class, 1, Side.SERVER);
    }

    // 放送再生用
    public static class AnnounceHandler implements IMessageHandler<MessageAnnounce, IMessage> {
        @Override
        public IMessage onMessage(MessageAnnounce message, MessageContext ctx) {
            if (message.stopCommand) {
                AnnounceManager.INSTANCE.stopAnnounce();
            } else {
                AnnounceManager.INSTANCE.startAnnounce(message);
            }
            return null;
        }
    }

    // 設定保存用
    public static class ConfigHandler implements IMessageHandler<MessageConfig, IMessage> {
        @Override
        public IMessage onMessage(MessageConfig message, MessageContext ctx) {
            TileEntity te = ctx.getServerHandler().playerEntity.worldObj.getTileEntity(message.x, message.y, message.z);
            if (te instanceof TileEntityAnnouncer) {
                ((TileEntityAnnouncer) te).setScriptName(message.scriptName);
                te.markDirty(); // 保存を確定させる
                StationAnnounceMod.logger.info("Config saved for TileEntity: " + message.scriptName);
            }
            return null;
        }
    }

    // 設定の同期用
    public static void sendSelectorUpdate(int x, int y, int z, String link, String data, int type) {
        // ここで設定更新用のパケットを送る
        // MessageSelectorUpdate というパケットクラスを新規作成する必要があります
    }

    // 車両データの通知用
    public static void sendTrainData(String linkKey, String value) {
        // linkKeyとvalueを全クライアント（または該当する装置の周囲）に送る
        // MessageTrainData というパケットクラスを新規作成する必要があります
    }
}
