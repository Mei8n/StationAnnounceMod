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
        INSTANCE.registerMessage(MessageSelectorUpdate.Handler.class, MessageSelectorUpdate.class, 2, Side.SERVER);
        INSTANCE.registerMessage(MessageTrainData.Handler.class, MessageTrainData.class, 3, Side.CLIENT);
        INSTANCE.registerMessage(MessageAnnouncerUpdate.Handler.class, MessageAnnouncerUpdate.class, 4, Side.SERVER);
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
        INSTANCE.sendToServer(new MessageSelectorUpdate(x, y, z, link, data, type));
    }

    public static void sendTrainData(String linkKey, String value) {
        // 全クライアントに通知（パケット最小化のためにはsendToAll推奨）
        INSTANCE.sendToAll(new MessageTrainData(linkKey, value));
    }

    public static void sendAnnouncerUpdate(int x, int y, int z, String scriptName, String linkKey) {
        INSTANCE.sendToServer(new MessageAnnouncerUpdate(x, y, z, scriptName, linkKey));
    }
}
