package jp.me1han.sam;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import java.io.File;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // 必要に応じて設定の読み込みなど
    }

    public void init(FMLInitializationEvent event) {
        // 共通の初期化処理
    }

    public void postInit(FMLPostInitializationEvent event) {
    }

    public void serverStarting(FMLServerStartingEvent event) {
    }

    /**
     * サーバー側では何もしない（ClientProxy側で上書きされる）
     */
    public void addResourcePack(File zipFile) {
        // サーバー側には音声再生がないため、何もしない
    }
}
