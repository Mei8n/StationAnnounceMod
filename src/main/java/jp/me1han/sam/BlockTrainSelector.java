package jp.me1han.sam;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockTrainSelector extends BlockContainer {

    public BlockTrainSelector() {
        super(Material.iron);
        setBlockName("sam.train_selector");
        setBlockTextureName("stationannouncemod:train_selector");
        setCreativeTab(StationAnnounceMod.tabSAM);
        // 線路の下に置きやすいよう、少し高さを下げる設定（任意）
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.5F, 1.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityTrainSelector();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            // GUIを開く (ID: 1 と定義)
            player.openGui(StationAnnounceMod.instance, 1, world, x, y, z);
        }
        return true;
    }

    @Override
    public int getRenderType() { return 0; }
    @Override
    public boolean renderAsNormalBlock() { return false; }
    @Override
    public boolean isOpaqueCube() { return false; }
}
