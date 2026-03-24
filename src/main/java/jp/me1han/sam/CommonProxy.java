package jp.me1han.sam;

import java.io.File;
// (既存のimport文...)

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // 既存のコードは残しておいてOKです
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}

    /**
     * サーバー側では何もしない（ClientProxy側で上書きされる）
     */
    public void addResourcePack(File zipFile) {
        // サーバー側には音声再生がないため、何もしない
    }
}
