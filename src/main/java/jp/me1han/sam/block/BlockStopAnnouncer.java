package jp.me1han.sam.block;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.TileEntityStopAnnouncer;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockStopAnnouncer extends Block implements ITileEntityProvider {

    public BlockStopAnnouncer() {
        super(Material.iron);
        this.setBlockName("sam.stop_announcer");
        this.setBlockTextureName("stationannouncemod:stop_announcer");
        this.setCreativeTab(StationAnnounceModCore.tabSAM);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(StationAnnounceModCore.instance, StationAnnounceModCore.GUI_ID_STOP_ANNOUNCER, world, x, y, z);
        }
        return true;
    }

    @Override
    public void onNeighborBlockChange(net.minecraft.world.World world, int x, int y, int z, net.minecraft.block.Block block) {
        if (!world.isRemote) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof jp.me1han.sam.render.TileEntityStopAnnouncer) {
                boolean powered = world.isBlockIndirectlyGettingPowered(x, y, z) || world.isBlockIndirectlyGettingPowered(x, y, z);
                ((jp.me1han.sam.render.TileEntityStopAnnouncer) te).onRedstoneUpdate(powered);
            }
        }
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityStopAnnouncer();
    }
}
