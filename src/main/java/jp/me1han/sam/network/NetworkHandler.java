package jp.me1han.sam.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import jp.me1han.sam.client.AnnounceManager;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.TileEntityAnnouncer;
import jp.me1han.sam.render.TileEntityTrainTypeSelector;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class NetworkHandler {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("SAM_CHANNEL");

    public static void init() {
        // ID 0: サーバー -> クライアント (再生命令)
        INSTANCE.registerMessage(AnnounceHandler.class, MessageAnnounce.class, 0, Side.CLIENT);
        // ID 1: クライアント -> サーバー (設定保存)
        INSTANCE.registerMessage(ConfigHandler.class, MessageConfig.class, 1, Side.SERVER);
        // ID 2: クライアント -> サーバー (列車選別装置の設定保存)
        INSTANCE.registerMessage(TrainTypeConfigHandler.class, MessageTrainTypeConfig.class, 2, Side.SERVER);
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
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = ctx.getServerHandler().playerEntity.worldObj.getTileEntity(message.x, message.y, message.z);

            if (te instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer announcer = (TileEntityAnnouncer) te;
                announcer.setScriptName(message.scriptName);
                announcer.linkKey = message.linkKey;

                announcer.markDirty();

                world.markBlockForUpdate(message.x, message.y, message.z);

                StationAnnounceModCore.logger.info("Config saved: " + message.scriptName + " with key: " + message.linkKey);
            }
            return null;
        }
    }

    public static class TrainTypeConfigHandler implements IMessageHandler<MessageTrainTypeConfig, IMessage> {
        @Override
        public IMessage onMessage(MessageTrainTypeConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = ctx.getServerHandler().playerEntity.worldObj.getTileEntity(message.x, message.y, message.z);

            if (te instanceof TileEntityTrainTypeSelector) {
                TileEntityTrainTypeSelector selector = (TileEntityTrainTypeSelector) te;

                selector.conditions = message.conditions;

                selector.markDirty();
                
                world.markBlockForUpdate(message.x, message.y, message.z);
                StationAnnounceModCore.logger.info("TrainSelector Config saved: " + message.conditions.size() + " conditions.");
            }
            return null;
        }
    }
}
