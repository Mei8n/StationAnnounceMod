package jp.me1han.sam.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import jp.me1han.sam.SpeakerRegistry;
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
        // Discriminator 7 retired (unused debug events).
        INSTANCE.registerMessage(AwarenessConfigHandler.class, PacketAwarenessConfig.class, 8, Side.SERVER);
        INSTANCE.registerMessage(DepartureMelodyConfigHandler.class, PacketDepartureMelodyConfig.class, 9, Side.SERVER);
        INSTANCE.registerMessage(DepartureControlHandler.class, PacketDepartureControl.class, 10, Side.CLIENT);
        INSTANCE.registerMessage(PacketDepartureSwitchConfig.Handler.class, PacketDepartureSwitchConfig.class, 11, Side.SERVER);
        INSTANCE.registerMessage(StopHandler.class, PacketAnnounceStop.class, 12, Side.CLIENT);
        INSTANCE.registerMessage(DepartureStartHandler.class, PacketDepartureStart.class, 13, Side.CLIENT);
        INSTANCE.registerMessage(FinishedHandler.class, PacketSessionFinished.class, 14, Side.SERVER);
        INSTANCE.registerMessage(MissingSpeakersHandler.class, PacketMissingSpeakers.class, 15, Side.SERVER);
        INSTANCE.registerMessage(SpeakerFallbackHandler.class, PacketSpeakerFallback.class, 16, Side.CLIENT);
    }

    // --- クライアント側受信 ---
    public static class AnnounceHandler implements IMessageHandler<PacketAnnounce, IMessage> {
        @Override
        public IMessage onMessage(PacketAnnounce message, MessageContext ctx) {
            jp.me1han.sam.client.AnnounceManager.INSTANCE.receive(message);
            return null;
        }
    }

    public static class DepartureControlHandler implements IMessageHandler<PacketDepartureControl, IMessage> {
        @Override public IMessage onMessage(PacketDepartureControl message, MessageContext ctx) {
            jp.me1han.sam.client.AnnounceManager.INSTANCE.receive(message);
            return null;
        }
    }


    public static class StopHandler implements IMessageHandler<PacketAnnounceStop, IMessage> {
        @Override public IMessage onMessage(PacketAnnounceStop message, MessageContext ctx) {
            jp.me1han.sam.client.AnnounceManager.INSTANCE.receive(message); return null;
        }
    }
    public static class DepartureStartHandler implements IMessageHandler<PacketDepartureStart, IMessage> {
        @Override public IMessage onMessage(PacketDepartureStart message, MessageContext ctx) {
            jp.me1han.sam.client.AnnounceManager.INSTANCE.receive(message); return null;
        }
    }
    public static class FinishedHandler implements IMessageHandler<PacketSessionFinished, IMessage> {
        @Override public IMessage onMessage(PacketSessionFinished message, MessageContext ctx) {
            final net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerTaskQueue.INSTANCE.enqueue(() -> ServerSessions.finished(player, message.sessionId));
            return null;
        }
    }
    public static class MissingSpeakersHandler implements IMessageHandler<PacketMissingSpeakers, IMessage> {
        @Override public IMessage onMessage(PacketMissingSpeakers message, MessageContext ctx) {
            final net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerTaskQueue.INSTANCE.enqueue(() -> ServerSessions.missing(player, message));
            return null;
        }
    }
    public static class SpeakerFallbackHandler implements IMessageHandler<PacketSpeakerFallback, IMessage> {
        @Override public IMessage onMessage(PacketSpeakerFallback message, MessageContext ctx) {
            jp.me1han.sam.client.AnnounceManager.INSTANCE.receive(message); return null;
        }
    }
    public static class TrainTypeConfigHandler implements IMessageHandler<PacketTrainTypeConfig, IMessage> {
        @Override public IMessage onMessage(PacketTrainTypeConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, TileEntityTrainTypeSelector.class, tile -> {
                if (!ConfigAccess.key(m.linkKey) || m.conditions == null || m.conditions.size() > PacketLimits.CONDITIONS) return;
                for (jp.me1han.sam.api.TrainTypeCondition condition : m.conditions)
                    if (condition == null || !PacketLimits.string(condition.key, PacketLimits.NAME) || condition.type < 0 || condition.type > 3) return;
                ConfigAccess.change(tile, () -> {
                    tile.conditions = new java.util.ArrayList<>(m.conditions);
                    tile.linkKey = ConfigAccess.normalize(m.linkKey); tile.isControlCar = m.isControlCar;
                });
            }); return null;
        }
    }
    public static class StartAnnouncerConfigHandler implements IMessageHandler<PacketStartAnnouncerConfig, IMessage> {
        @Override public IMessage onMessage(PacketStartAnnouncerConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, TileEntityStartAnnouncer.class, tile -> {
                if (!ConfigAccess.key(m.linkKey)) return;
                ConfigAccess.change(tile, () -> { tile.linkKey = ConfigAccess.normalize(m.linkKey); tile.isControlCar = m.isControlCar; });
            }); return null;
        }
    }
    public static class StopAnnouncerConfigHandler implements IMessageHandler<PacketStopAnnouncerConfig, IMessage> {
        @Override public IMessage onMessage(PacketStopAnnouncerConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, TileEntityStopAnnouncer.class, tile -> {
                if (!ConfigAccess.key(m.linkKey)) return;
                ConfigAccess.change(tile, () -> { tile.linkKey = ConfigAccess.normalize(m.linkKey); tile.isControlCar = m.isControlCar; });
            }); return null;
        }
    }
    public static class SpeakerConfigHandler implements IMessageHandler<PacketSpeakerConfig, IMessage> {
        @Override public IMessage onMessage(PacketSpeakerConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, TileEntitySpeaker.class, tile -> {
                if (!ConfigAccess.key(m.linkKey) || !PacketLimits.speaker(m.range, m.volume)) return;
                tile.applyConfig(m.linkKey, m.range, m.volume);
            }); return null;
        }
    }
    public static class AwarenessConfigHandler implements IMessageHandler<PacketAwarenessConfig, IMessage> {
        @Override public IMessage onMessage(PacketAwarenessConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, TileEntityAwarenessAnnouncer.class, tile -> {
                if (!ConfigAccess.key(m.linkKey) || !PacketLimits.sounds(m.soundList)
                    || m.intervalTicks < 20 || m.intervalTicks > PacketLimits.MAX_TICKS
                    || m.departureDelayTicks < 0 || m.departureDelayTicks > PacketLimits.MAX_TICKS) return;
                String sounds = TileEntityAwarenessAnnouncer.normalizeSoundList(m.soundList);
                if (ConfigAccess.normalize(m.linkKey).equals(tile.linkKey) && sounds.equals(tile.soundList)
                    && m.intervalTicks == tile.intervalTicks && m.randomOrder == tile.randomOrder
                    && m.allowOverlap == tile.allowOverlap && m.playAfterDeparture == tile.playAfterDeparture
                    && m.departureDelayTicks == tile.departureDelayTicks) return;
                ConfigAccess.change(tile, () -> tile.applyConfig(m.linkKey, sounds, m.intervalTicks,
                    m.randomOrder, m.allowOverlap, m.playAfterDeparture, m.departureDelayTicks));
            }); return null;
        }
    }
    public static class DepartureMelodyConfigHandler implements IMessageHandler<PacketDepartureMelodyConfig, IMessage> {
        @Override public IMessage onMessage(PacketDepartureMelodyConfig m, MessageContext ctx) {
            ConfigAccess.enqueue(ctx, m.x, m.y, m.z, TileEntityDepartureMelody.class, tile -> {
                if (!ConfigAccess.key(m.linkKey) || !PacketLimits.string(m.scriptName, PacketLimits.NAME)
                    || !PacketLimits.string(m.soundId, PacketLimits.NAME)) return;
                tile.applyConfig(m.linkKey, m.soundId, m.scriptName);
            }); return null;
        }
    }
}
