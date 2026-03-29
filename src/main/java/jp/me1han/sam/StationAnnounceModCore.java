package jp.me1han.sam;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import jp.me1han.sam.block.*;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.network.SAMGuiHandler;
import jp.me1han.sam.render.*;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = StationAnnounceModCore.MOD_ID, name = StationAnnounceModCore.MOD_NAME, version = StationAnnounceModCore.VERSION)
public class StationAnnounceModCore {
    public static final String MOD_ID = "stationannouncemod";
    public static final String MOD_NAME = "Station Announce Mod";
    public static final String VERSION = "v0.1.0-beta";

    public static final int GUI_ID_ANNOUNCER = 0;
    public static final int GUI_ID_TRAIN_TYPE_SELECTOR = 1;
    public static final int GUI_ID_DEBUG_RECEIVER = 2;
    public static final int GUI_ID_START_ANNOUNCER = 4;
    public static final int GUI_ID_STOP_ANNOUNCER = 5;
    public static final int GUI_ID_SPEAKER = 6;

    public static java.io.File samPacksDir;

    @Mod.Instance("stationannouncemod")
    public static StationAnnounceModCore instance;

    @SidedProxy(clientSide = "jp.me1han.sam.ClientProxy", serverSide = "jp.me1han.sam.CommonProxy")
    public static CommonProxy proxy;

    public static final Logger logger = LogManager.getLogger("SAM");

    public static final CreativeTabs tabSAM = new CreativeTabSAM("SAM");

    public static Block blockAnnouncer;
    public static Block blockStartAnnouncer;
    public static Block blockStopAnnouncer;
    public static Block blockTrainTypeSelector;
    public static Block blockDebugReceiver;
    public static Block blockSpeaker;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        java.io.File mcDir = event.getModConfigurationDirectory().getParentFile();
        samPacksDir = new java.io.File(mcDir, "mods" + java.io.File.separator + "SAMpacks");

        blockAnnouncer = new BlockAnnouncer();
        GameRegistry.registerBlock(blockAnnouncer, "blockAnnouncer");

        blockStartAnnouncer = new BlockStartAnnouncer();
        GameRegistry.registerBlock(blockStartAnnouncer, "blockStartAnnouncer");

        blockStopAnnouncer = new BlockStopAnnouncer();
        GameRegistry.registerBlock(blockStopAnnouncer, "blockStopAnnouncer");

        blockTrainTypeSelector = new BlockTrainTypeSelector();
        GameRegistry.registerBlock(blockTrainTypeSelector, "trainTypeSelector");

        blockSpeaker = new BlockSpeaker();
        GameRegistry.registerBlock(blockSpeaker, "blockSpeaker");

        blockDebugReceiver = new BlockDebugReceiver();
        GameRegistry.registerBlock(blockDebugReceiver, "blockDebugReceiver");

        GameRegistry.registerTileEntity(TileEntityAnnouncer.class, "tileEntityAnnouncer");
        GameRegistry.registerTileEntity(TileEntityStartAnnouncer.class, "tileStartAnnouncer");
        GameRegistry.registerTileEntity(TileEntityStopAnnouncer.class, "tileStopAnnouncer");
        GameRegistry.registerTileEntity(TileEntityTrainTypeSelector.class, "tileTrainTypeSelector");
        GameRegistry.registerTileEntity(TileEntitySpeaker.class, "tileSpeaker");
        GameRegistry.registerTileEntity(TileEntityDebugReceiver.class, "tileDebugReceiver");

        NetworkHandler.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new SAMGuiHandler());
        AnnouncePackLoader.loadPacks();
        proxy.init(event);
    }
}
