package jp.me1han.sam;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.client.registry.ClientRegistry; // 追加
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import java.io.File;
import java.util.List;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(cpw.mods.fml.common.event.FMLInitializationEvent event) {
        super.init(event);
        FMLCommonHandler.instance().bus().register(AnnounceManager.INSTANCE);

        // 列車選別装置のビーコンレンダラーを登録
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityTrainSelector.class, new TrainSelectorRenderer());
    }

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
            StationAnnounceMod.logger.error("Failed to register resource pack: " + zipFile.getName());
        }
    }
}
