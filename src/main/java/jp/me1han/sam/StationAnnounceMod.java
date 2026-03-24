package jp.me1han.sam;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

@Mod(modid = "stationannouncemod", name = "StationAnnounceMod", version = "1.0.0")
public class StationAnnounceMod {

    @Mod.Instance("stationannouncemod")
    public static StationAnnounceMod instance;

    // 放送用ブロックの変数
    public static Block blockAnnouncer;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // ブロックのインスタンス化
        blockAnnouncer = new BlockAnnouncer().setBlockName("announcer_block");

        // Minecraftへの登録
        GameRegistry.registerBlock(blockAnnouncer, "announcer_block");

        // TileEntity登録
        GameRegistry.registerTileEntity(TileEntityAnnouncer.class, "tile_entity_announcer");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // ここでレシピを追加
    }
}
