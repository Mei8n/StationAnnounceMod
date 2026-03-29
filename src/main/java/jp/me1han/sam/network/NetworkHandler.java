package jp.me1han.sam.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class NetworkHandler {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("SAM_CHANNEL");

    public static void init() {
        // 音声再生処理のサーバー -> クライアント
        INSTANCE.registerMessage(AnnounceHandler.class, PacketAnnounce.class, 0, Side.CLIENT);

        // 各種GUI設定のクライアント -> サーバー
        // ※AnnouncerとDebugReceiverはパケットクラス内のHandlerを登録しています
        INSTANCE.registerMessage(PacketConfig.Handler.class, PacketConfig.class, 1, Side.SERVER);
        INSTANCE.registerMessage(TrainTypeConfigHandler.class, PacketTrainTypeConfig.class, 2, Side.SERVER);
        INSTANCE.registerMessage(PacketDebugConfig.Handler.class, PacketDebugConfig.class, 3, Side.SERVER);
        INSTANCE.registerMessage(StartAnnouncerConfigHandler.class, PacketStartAnnouncerConfig.class, 4, Side.SERVER);
        INSTANCE.registerMessage(StopAnnouncerConfigHandler.class, PacketStopAnnouncerConfig.class, 5, Side.SERVER);
        INSTANCE.registerMessage(SpeakerConfigHandler.class, PacketSpeakerConfig.class, 6, Side.SERVER);
        INSTANCE.registerMessage(DebugAnnounceEventHandler.class, PacketDebugAnnounceEvent.class, 7, Side.SERVER);
    }

    /**
     * Debug message to players if DebugReceiver with matching linkKey exists
     */
    public static void sendDebugMessage(World world, String linkKey, String message) {
        if (world == null || world.isRemote) return;

        // Check if any DebugReceiver with matching linkKey exists
        boolean debugReceiverFound = false;
        for (Object obj : world.loadedTileEntityList) {
            if (obj instanceof TileEntityDebugReceiver) {
                TileEntityDebugReceiver receiver = (TileEntityDebugReceiver) obj;
                if (receiver.linkKey != null && linkKey != null &&
                    receiver.linkKey.trim().equals(linkKey.trim())) {
                    debugReceiverFound = true;
                    break;
                }
            }
        }

        // Only send to chat if debug receiver exists
        if (debugReceiverFound) {
            for (Object obj : world.playerEntities) {
                net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) obj;
                player.addChatMessage(new net.minecraft.util.ChatComponentText(message));
            }
        }
    }

    // --- クライアント側受信 ---
    public static class AnnounceHandler implements IMessageHandler<PacketAnnounce, IMessage> {
        @Override
        public IMessage onMessage(PacketAnnounce message, MessageContext ctx) {
            if (message.stopCommand) {
                jp.me1han.sam.client.AnnounceManager.INSTANCE.stopAnnounce(message.linkKey);
            } else {
                jp.me1han.sam.client.AnnounceManager.INSTANCE.startAnnounce(message);
            }
            return null;
        }
    }

    // --- サーバー側受信 ---
    public static class TrainTypeConfigHandler implements IMessageHandler<PacketTrainTypeConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketTrainTypeConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);
            StationAnnounceModCore.logger.info("[SAM-DEBUG] TrainTypeSelector Config Received! linkKey=" + message.linkKey);

            if (te instanceof TileEntityTrainTypeSelector) {
                TileEntityTrainTypeSelector selector = (TileEntityTrainTypeSelector) te;
                selector.conditions = message.conditions;
                selector.linkKey = message.linkKey;
                selector.isControlCar = message.isControlCar;
                selector.markDirty();
                world.markBlockForUpdate(message.x, message.y, message.z);
            }
            return null;
        }
    }

    public static class StartAnnouncerConfigHandler implements IMessageHandler<PacketStartAnnouncerConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketStartAnnouncerConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);
            StationAnnounceModCore.logger.info("[SAM-DEBUG] StartAnnouncer Config Received! linkKey=" + message.linkKey);

            if (te instanceof TileEntityStartAnnouncer) {
                TileEntityStartAnnouncer announcer = (TileEntityStartAnnouncer) te;
                announcer.linkKey = message.linkKey;
                announcer.isControlCar = message.isControlCar;
                announcer.markDirty();
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
            NetworkHandler.sendDebugMessage(world, message.linkKey, "[SAM-DEBUG] StopAnnouncer Config Received! linkKey=" + message.linkKey);

            if (te instanceof TileEntityStopAnnouncer) {
                TileEntityStopAnnouncer announcer = (TileEntityStopAnnouncer) te;
                announcer.linkKey = message.linkKey;
                announcer.isControlCar = message.isControlCar;
                announcer.markDirty();
                world.markBlockForUpdate(message.x, message.y, message.z);
            }
            return null;
        }
    }

    public static class SpeakerConfigHandler implements IMessageHandler<PacketSpeakerConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketSpeakerConfig message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);
            NetworkHandler.sendDebugMessage(world, message.linkKey, "[SAM-DEBUG] Speaker Config Received! pos=" + message.x + "," + message.y + "," + message.z + " linkKey=" + message.linkKey + " range=" + message.range + " volume=" + message.volume);

            if (te instanceof TileEntitySpeaker) {
                TileEntitySpeaker speaker = (TileEntitySpeaker) te;
                speaker.linkKey = message.linkKey;
                speaker.range = message.range;
                speaker.volume = message.volume;
                speaker.markDirty();
                world.markBlockForUpdate(message.x, message.y, message.z);
            }
            return null;
        }
    }

    public static class DebugAnnounceEventHandler implements IMessageHandler<PacketDebugAnnounceEvent, IMessage> {
        @Override
        public IMessage onMessage(PacketDebugAnnounceEvent message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;

            String msg = "";
            if ("START".equals(message.eventType)) {
                msg = "§b[SAM-PLAYBACK] START§f key=" + message.linkKey + " sound=" + message.soundId + (message.playLocalSound ? " +local" : "");
            } else if ("STOP".equals(message.eventType)) {
                msg = "§c[SAM-PLAYBACK] STOP§f key=" + message.linkKey;
            } else if ("PLAY".equals(message.eventType)) {
                msg = "§a[SAM-PLAYBACK] PLAYING§f key=" + message.linkKey + " speakers=" + message.matchedSpeakers + " sound=" + message.soundId;
            }

            if (!msg.isEmpty()) {
                for (Object obj : world.playerEntities) {
                    net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) obj;
                    player.addChatMessage(new net.minecraft.util.ChatComponentText(msg));
                }
            }
            return null;
        }
    }
}
