package jp.me1han.sam.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import jp.me1han.sam.client.AnnounceManager;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class NetworkHandler {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("SAM_CHANNEL");

    public static void init() {
        //音声再生処理のサーバー -> クライアント
        INSTANCE.registerMessage(AnnounceHandler.class, PacketAnnounce.class, 0, Side.CLIENT);
        //音声再生処理のクライアント -> サーバー
        INSTANCE.registerMessage(ConfigHandler.class, PacketConfig.class, 1, Side.SERVER);
        // 列車選別装置
        INSTANCE.registerMessage(TrainTypeConfigHandler.class, PacketTrainTypeConfig.class, 2, Side.SERVER);
        //デバッグレシーバー
        INSTANCE.registerMessage(PacketDebugConfig.Handler.class, PacketDebugConfig.class, 3, Side.SERVER);
        //放送開始装置
        INSTANCE.registerMessage(StartAnnouncerConfigHandler.class, PacketStartAnnouncerConfig.class, 4, Side.SERVER);
        //放送停止装置
        INSTANCE.registerMessage(StopAnnouncerConfigHandler.class, PacketStopAnnouncerConfig.class, 5, Side.SERVER);
    }

    public static class AnnounceHandler implements IMessageHandler<PacketAnnounce, IMessage> {
        @Override
        public IMessage onMessage(PacketAnnounce message, MessageContext ctx) {
            if (message.stopCommand) {
                AnnounceManager.INSTANCE.stopAnnounce();
            } else {
                AnnounceManager.INSTANCE.startAnnounce(message);
            }
            return null;
        }
    }

    public static class ConfigHandler implements IMessageHandler<PacketConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = ctx.getServerHandler().playerEntity.worldObj.getTileEntity(message.x, message.y, message.z);

            if (te instanceof TileEntityAnnouncer) {
                TileEntityAnnouncer announcer = (TileEntityAnnouncer) te;
                announcer.setScriptName(message.scriptName);
                announcer.linkKey = message.linkKey;

                announcer.markDirty();

                world.markBlockForUpdate(message.x, message.y, message.z);

            }
            return null;
        }
    }

    public static class TrainTypeConfigHandler implements IMessageHandler<PacketTrainTypeConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketTrainTypeConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);

            if (te instanceof TileEntityTrainTypeSelector) {
                TileEntityTrainTypeSelector selector = (TileEntityTrainTypeSelector) te;

                selector.conditions = message.conditions;
                selector.linkKey = message.linkKey;
                selector.isControlCar = message.isControlCar;

                selector.markDirty();
                world.markBlockForUpdate(message.x, message.y, message.z);
                StationAnnounceModCore.logger.info("TrainSelector Config saved: " + message.conditions.size() + " conditions.");
            }
            return null;
        }
    }

    public static class StartAnnouncerConfigHandler implements IMessageHandler<PacketStartAnnouncerConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketStartAnnouncerConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);
            if (te instanceof TileEntityStartAnnouncer) {
                TileEntityStartAnnouncer startAnnouncer = (TileEntityStartAnnouncer) te;
                startAnnouncer.linkKey = message.linkKey;
                startAnnouncer.markDirty();
                world.markBlockForUpdate(message.x, message.y, message.z);
            }
            return null;
        }
    }

    public static class StopAnnouncerConfigHandler implements IMessageHandler<PacketStopAnnouncerConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketStopAnnouncerConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);
            if (te instanceof TileEntityStopAnnouncer) {
                TileEntityStopAnnouncer stopAnnouncer = (TileEntityStopAnnouncer) te;
                stopAnnouncer.linkKey = message.linkKey;
                stopAnnouncer.markDirty();
                world.markBlockForUpdate(message.x, message.y, message.z);
            }
            return null;
        }
    }

    public static class DebugConfigHandler implements IMessageHandler<PacketDebugConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketDebugConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);
            if (te instanceof TileEntityDebugReceiver) {
                TileEntityDebugReceiver debug = (TileEntityDebugReceiver) te;
                debug.linkKey = message.linkKey;
                debug.markDirty();
                world.markBlockForUpdate(message.x, message.y, message.z);
            }
            return null;
        }
    }
}
