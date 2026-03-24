package jp.me1han.sam;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer; // ★追加
import net.minecraft.tileentity.TileEntity;      // ★追加
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
            if (world.isBlockIndirectlyGettingPowered(x, y, z)) {
                NetworkHandler.INSTANCE.sendToAllAround(
                    new MessageAnnounce(true),
                    new NetworkRegistry.TargetPoint(world.provider.dimensionId, x, y, z, 64)
                );
            }
        }
    }

    // ★戻り値の型を TileEntity にし、import net.minecraft.tileentity.TileEntity を通します
    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileEntityStopAnnouncer();
    }

    // ★EntityPlayer のインポートと openGui の引数（StationAnnounceMod.instance）を修正
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            // 第1引数はクラス名ではなく、instance (実体) を渡します。
            // 第2引数の '2' は、GuiHandler で StopAnnouncer 用に割り当てるIDです。
            player.openGui(StationAnnounceMod.instance, 2, world, x, y, z);
        }
        return true;
    }
}
