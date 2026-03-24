package jp.me1han.sam;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.world.World;
import cpw.mods.fml.common.network.NetworkRegistry;

public class BlockStopAnnouncer extends Block {

    public BlockStopAnnouncer() {
        super(Material.iron);
        setBlockName("sam.stop_announcer");
        setBlockTextureName("stationannouncemod:stop_announcer");
        setCreativeTab(StationAnnounceMod.tabSAM);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighborBlock) {
        if (!world.isRemote) {
            // レッドストーン信号が入っているか確認
            if (world.isBlockIndirectlyGettingPowered(x, y, z)) {
                // 停止パケットを送信
                // AnnounceData(true) は stopCommand が true になるコンストラクタ
                NetworkHandler.INSTANCE.sendToAllAround(
                    new MessageAnnounce(true),
                    new NetworkRegistry.TargetPoint(world.provider.dimensionId, x, y, z, 64)
                );
            }
        }
    }
}
