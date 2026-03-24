package jp.me1han.sam;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockAnnouncer extends BlockContainer {
    protected BlockAnnouncer() {
        super(Material.iron); // 鉄ブロックの性質
        setHardness(2.0F);    // 硬さ
        setResistance(10.0F); // 爆破耐性
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAnnouncer();
    }

    // 隣接するブロックが変化（RS信号など）したときに呼ばれる
    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block block) {
        if (!world.isRemote) { // サーバー側でのみ処理
            boolean isPowered = world.isBlockIndirectlyGettingPowered(x, y, z);
            TileEntityAnnouncer te = (TileEntityAnnouncer) world.getTileEntity(x, y, z);
            if (te != null) {
                te.onRedstoneUpdate(isPowered);
            }
        }
    }
}
