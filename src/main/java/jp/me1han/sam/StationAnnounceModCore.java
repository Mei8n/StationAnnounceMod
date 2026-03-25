package jp.me1han.sam;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import jp.me1han.sam.block.BlockAnnouncer;
import jp.me1han.sam.block.BlockStopAnnouncer;
import jp.me1han.sam.block.BlockTrainTypeSelector;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.network.SAMGuiHandler;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "stationannouncemod", name = "Station Announce Mod", version = "1.0")
public class StationAnnounceModCore {

    @Mod.Instance("stationannouncemod")
    public static StationAnnounceModCore instance;

    @SidedProxy(clientSide = "jp.me1han.sam.ClientProxy", serverSide = "jp.me1han.sam.CommonProxy")
    public static CommonProxy proxy;

    public static final Logger logger = LogManager.getLogger("SAM");

    public static final CreativeTabs tabSAM = new CreativeTabSAM("SAM");

    public static Block blockAnnouncer;
    public static Block blockStopAnnouncer;
    public static Block blockTrainTypeSelector;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        blockAnnouncer = new BlockAnnouncer();
        GameRegistry.registerBlock(blockAnnouncer, "blockAnnouncer");

        blockStopAnnouncer = new BlockStopAnnouncer();
        GameRegistry.registerBlock(blockStopAnnouncer, "blockStopAnnouncer");

        blockTrainTypeSelector = new BlockTrainTypeSelector();
        GameRegistry.registerBlock(blockTrainTypeSelector, "trainTypeSelector");

        GameRegistry.registerTileEntity(TileEntityAnnouncer.class, "tileEntityAnnouncer");
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
