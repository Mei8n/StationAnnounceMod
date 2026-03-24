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

@Mod(
    modid = "stationannouncemod",
    name = "Station Announce Mod",
    version = "0.1-alpha",
    dependencies = "after:RTM ; after:NGTLib"
)

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
    public static Block blockTrainSelector;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // 1. ブロックのインスタンス化
        // コンストラクタ内で setCreativeTab(StationAnnounceMod.tabSAM) が呼ばれます
        blockAnnouncer = new BlockAnnouncer();
        blockStopAnnouncer = new BlockStopAnnouncer();
        blockTrainSelector = new BlockTrainSelector();

        // 2. ブロックの登録
        GameRegistry.registerBlock(blockAnnouncer, "blockAnnouncer");
        GameRegistry.registerBlock(blockStopAnnouncer, "blockStopAnnouncer");
        GameRegistry.registerBlock(blockTrainSelector, "blockTrainSelector");

        // 3. TileEntityの登録
        GameRegistry.registerTileEntity(TileEntityAnnouncer.class, "tileEntityAnnouncer");
        GameRegistry.registerTileEntity(TileEntityTrainSelector.class, "tileEntityTrainSelector");

        // 4. 通信とプロキシの初期化
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
