package jp.me1han.sam;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import java.io.File;
import java.util.List;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(cpw.mods.fml.common.event.FMLInitializationEvent event) {
        super.init(event);
        // 必要に応じてここで描画登録などを行います
    }

    /**
     * 指定されたZipファイルをリソースパックとしてMinecraftに登録する
     */
    public void addResourcePack(File zipFile) {
        try {
            // Minecraft内部のデフォルトリソースパックリストを取得
            List<IResourcePack> defaultPacks = ReflectionHelper.getPrivateValue(
                Minecraft.class,
                Minecraft.getMinecraft(),
                "defaultResourcePacks",
                "field_110449_ao"
            );

            // SAM専用のリソースパッククラスを追加
            defaultPacks.add(new SAMResourcePack(zipFile));

            // リソースを再読み込みして音声を有効化
            Minecraft.getMinecraft().refreshResources();

        } catch (Exception e) {
            System.err.println("Failed to register resource pack: " + zipFile.getName());
            e.printStackTrace();
        }
    }
}
