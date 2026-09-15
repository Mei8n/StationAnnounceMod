package jp.me1han.sam;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.ReflectionHelper;
import jp.me1han.sam.client.AnnounceManager;
import jp.me1han.sam.client.MetadataCopyHandler;
import jp.me1han.sam.client.SAMResourcePack;
import jp.me1han.sam.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraftforge.common.MinecraftForge;

import java.io.File;
import java.util.List;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(cpw.mods.fml.common.event.FMLInitializationEvent event) {
        super.init(event);
        FMLCommonHandler.instance().bus().register(AnnounceManager.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new MetadataCopyHandler());
        this.registerRenderers();
    }

    @Override
    public void addResourcePack(File zipFile) {
        try {
            List<IResourcePack> defaultPacks = ReflectionHelper.getPrivateValue(
                Minecraft.class,
                Minecraft.getMinecraft(),
                "defaultResourcePacks", "field_110449_ao", "ap"
            );
            defaultPacks.add(new SAMResourcePack(zipFile));
            Minecraft.getMinecraft().refreshResources();
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("Failed to register resource pack: " + zipFile.getName());
        }
    }

    @Override
    public void registerRenderers() {
        RendererDepartureSwitch switchRenderer = new RendererDepartureSwitch();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDepartureSwitch.class, switchRenderer);
        ((net.minecraft.client.resources.IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager())
            .registerReloadListener(jp.me1han.sam.client.SwitchMeshRenderer.INSTANCE);
        ClientRegistry.bindTileEntitySpecialRenderer(
            TileEntityTrainTypeSelector.class,
            new RendererTrainTypeSelector()
        );

        ClientRegistry.bindTileEntitySpecialRenderer(
            TileEntityStartAnnouncer.class,
            new RendererStartAnnouncer()
        );

        ClientRegistry.bindTileEntitySpecialRenderer(
            TileEntityStopAnnouncer.class,
            new RendererStopAnnouncer()
        );

        ClientRegistry.bindTileEntitySpecialRenderer(
            TileEntitySpeaker.class,
            new RendererSpeaker()
        );
    }

    @Override
    public void handleAnnouncePacket(jp.me1han.sam.network.PacketAnnounce message) {
        jp.me1han.sam.client.AnnounceManager.INSTANCE.receive(message);
    }


    @Override
    public Object getClientGuiElement(int ID, net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {
        if (ID == StationAnnounceModCore.GUI_ID_DEPARTURE_SWITCH_ITEM) {
            net.minecraft.item.ItemStack stack = player.getHeldItem();
            if (jp.me1han.sam.item.ItemDepartureSwitch.isSwitchItem(stack)) {
                return new jp.me1han.sam.gui.GuiDepartureSwitchItem(stack, player.inventory.currentItem);
            }
            return null;
        }
        if (ID == StationAnnounceModCore.GUI_ID_SPEAKER_ITEM) {
            net.minecraft.item.ItemStack stack = player.getHeldItem();
            if (jp.me1han.sam.item.ItemSpeaker.isSpeakerItem(stack)) {
                return new jp.me1han.sam.gui.GuiSpeakerItem(stack, player.inventory.currentItem);
            }
            return null;
        }
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(x, y, z);

        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_ANNOUNCER) {
            if (tile instanceof TileEntityAnnouncer) {
                return new jp.me1han.sam.gui.GuiAnnouncer(new jp.me1han.sam.container.ContainerAnnouncer((TileEntityAnnouncer) tile), (TileEntityAnnouncer) tile);
            }
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_TRAIN_TYPE_SELECTOR) {
            if (tile instanceof TileEntityTrainTypeSelector) {
                return new jp.me1han.sam.gui.GuiTrainTypeSelector(new jp.me1han.sam.container.ContainerTrainTypeSelector((TileEntityTrainTypeSelector) tile), (TileEntityTrainTypeSelector) tile);
            }
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_DEBUG_RECEIVER) {
            if (tile instanceof TileEntityDebugReceiver) {
                return new jp.me1han.sam.gui.GuiDebugReceiver((TileEntityDebugReceiver) tile);
            }
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_START_ANNOUNCER) {
            if (tile instanceof TileEntityStartAnnouncer) {
                return new jp.me1han.sam.gui.GuiStartAnnouncer((TileEntityStartAnnouncer) tile);
            }
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_STOP_ANNOUNCER) {
            if (tile instanceof TileEntityStopAnnouncer) {
                return new jp.me1han.sam.gui.GuiStopAnnouncer((TileEntityStopAnnouncer) tile);
            }
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_SPEAKER) {
            if (tile instanceof TileEntitySpeaker) {
                return new jp.me1han.sam.gui.GuiSpeaker((TileEntitySpeaker) tile);
            }
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_AWARENESS_ANNOUNCER && tile instanceof TileEntityAwarenessAnnouncer) {
            return new jp.me1han.sam.gui.GuiAwarenessAnnouncer((TileEntityAwarenessAnnouncer) tile);
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_DEPARTURE_MELODY && tile instanceof TileEntityDepartureMelody) {
            return new jp.me1han.sam.gui.GuiDepartureMelody((TileEntityDepartureMelody) tile);
        }
        if (ID == StationAnnounceModCore.GUI_ID_DEPARTURE_SWITCH && DepartureSwitchLink.isSwitch(tile)) {
            return new jp.me1han.sam.gui.GuiDepartureSwitch(tile);
        }
        if (ID == StationAnnounceModCore.GUI_ID_STATION_NAME && tile instanceof TileEntityStationNameAnnouncer) {
            return new jp.me1han.sam.gui.GuiStationNameAnnouncer((TileEntityStationNameAnnouncer)tile);
        }
        return null;
    }
}
