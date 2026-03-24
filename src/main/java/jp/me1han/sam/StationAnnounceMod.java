package jp.me1han.sam;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs; // 追加
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "stationannouncemod", name = "Station Announce Mod", version = "1.0")
public class StationAnnounceMod {

    @Mod.Instance("stationannouncemod")
    public static StationAnnounceMod instance;

    @SidedProxy(clientSide = "jp.me1han.sam.ClientProxy", serverSide = "jp.me1han.sam.CommonProxy")
    public static CommonProxy proxy;

    public static final Logger logger = LogManager.getLogger("SAM");

    // クリエイティブタブのインスタンス
    public static final CreativeTabs tabSAM = new SAMCreativeTab("SAM");

    public static Block blockAnnouncer;
    public static Block blockStopAnnouncer;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // 放送装置
        blockAnnouncer = new BlockAnnouncer();
        GameRegistry.registerBlock(blockAnnouncer, "blockAnnouncer");

        // 停止装置
        blockStopAnnouncer = new BlockStopAnnouncer();
        GameRegistry.registerBlock(blockStopAnnouncer, "blockStopAnnouncer");

        GameRegistry.registerTileEntity(TileEntityAnnouncer.class, "tileEntityAnnouncer");
        NetworkHandler.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new SAMGuiHandler());
        PackLoader.loadPacks();
        proxy.init(event);
    }
}
