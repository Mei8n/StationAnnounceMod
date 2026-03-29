package jp.me1han.sam.block;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockAnnouncer extends Block {

    public BlockAnnouncer() {
        super(Material.iron);
        setBlockName("sam.announcer");
        setBlockTextureName("stationannouncemod:announcer");
        setCreativeTab(StationAnnounceModCore.tabSAM);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileEntityAnnouncer();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(StationAnnounceModCore.instance, StationAnnounceModCore.GUI_ID_ANNOUNCER, world, x, y, z);
        }
        return true;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block block) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityAnnouncer) {
                ((TileEntityAnnouncer) te).onRedstoneUpdate(world.isBlockIndirectlyGettingPowered(x, y, z));
            }
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityAnnouncer) {
            ((TileEntityAnnouncer) te).forceStop();
        }

        super.breakBlock(world, x, y, z, block, meta);
    }
}
