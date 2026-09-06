package jp.me1han.sam.block;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.TileEntityDepartureMelody;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockDepartureMelody extends Block implements ITileEntityProvider {
    public BlockDepartureMelody() {
        super(Material.iron);
        this.setBlockName("sam.departure_melody");
        this.setBlockTextureName("stationannouncemod:departure_melody");
        this.setCreativeTab(StationAnnounceModCore.tabSAM);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(StationAnnounceModCore.instance, StationAnnounceModCore.GUI_ID_DEPARTURE_MELODY, world, x, y, z);
        }
        return true;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block block) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileEntityDepartureMelody) {
                ((TileEntityDepartureMelody) tile).onRedstoneUpdate(world.isBlockIndirectlyGettingPowered(x, y, z));
            }
        }
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityDepartureMelody();
    }
}
