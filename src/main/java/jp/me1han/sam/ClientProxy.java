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
        jp.me1han.sam.client.AnnounceManager.INSTANCE.startAnnounce(message);
    }

    @Override
    public Object getClientGuiElement(int ID, net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(x, y, z);

        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_ANNOUNCER) {
            return new jp.me1han.sam.gui.GuiAnnouncer(new jp.me1han.sam.container.ContainerAnnouncer((jp.me1han.sam.render.TileEntityAnnouncer) tile), (jp.me1han.sam.render.TileEntityAnnouncer) tile);
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_TRAIN_TYPE_SELECTOR) {
            return new jp.me1han.sam.gui.GuiTrainTypeSelector(new jp.me1han.sam.container.ContainerTrainTypeSelector((jp.me1han.sam.render.TileEntityTrainTypeSelector) tile), (jp.me1han.sam.render.TileEntityTrainTypeSelector) tile);
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_DEBUG_RECEIVER) {
            return new jp.me1han.sam.gui.GuiDebugReceiver((jp.me1han.sam.render.TileEntityDebugReceiver) tile);
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_START_ANNOUNCER) {
            return new jp.me1han.sam.gui.GuiStartAnnouncer((jp.me1han.sam.render.TileEntityStartAnnouncer) tile);
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_STOP_ANNOUNCER) {
            return new jp.me1han.sam.gui.GuiStopAnnouncer((jp.me1han.sam.render.TileEntityStopAnnouncer) tile);
        }
        if (ID == jp.me1han.sam.StationAnnounceModCore.GUI_ID_SPEAKER) {
            return new jp.me1han.sam.gui.GuiSpeaker((jp.me1han.sam.render.TileEntitySpeaker) world.getTileEntity(x, y, z));
        }
        return null;
    }
}
