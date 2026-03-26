package jp.me1han.sam.block;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.TileEntityDebugReceiver;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockDebugReceiver extends BlockContainer {
    public BlockDebugReceiver() {
        super(Material.iron);
        this.setBlockName("debugReceiver");
        this.setBlockTextureName("stationannouncemod:debug_receiver");
        this.setCreativeTab(StationAnnounceModCore.tabSAM);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hX, float hY, float hZ) {
        if (!world.isRemote) {
            player.openGui(StationAnnounceModCore.instance, StationAnnounceModCore.GUI_ID_DEBUG_RECEIVER, world, x, y, z);
        }
        return true;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block block) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityDebugReceiver) {
                ((TileEntityDebugReceiver) te).onRedstoneUpdate(world.isBlockIndirectlyGettingPowered(x, y, z));
            }
        }
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityDebugReceiver();
    }
}
