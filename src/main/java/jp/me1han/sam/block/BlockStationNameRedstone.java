package jp.me1han.sam.block;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.TileEntityStationNameRedstone;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class BlockStationNameRedstone extends Block implements ITileEntityProvider {
    public BlockStationNameRedstone() {
        super(Material.iron);
        setBlockName("sam.station_name_redstone");
        setBlockTextureName("stationannouncemod:station_name_redstone");
        setCreativeTab(StationAnnounceModCore.tabSAM);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileEntityStationNameRedstone(); }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
            int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) player.openGui(StationAnnounceModCore.instance,
            StationAnnounceModCore.GUI_ID_STATION_NAME, world, x, y, z);
        return true;
    }
    @Override public void onNeighborBlockChange(World world, int x, int y, int z, Block changed) {
        if (world.isRemote) return;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityStationNameRedstone)
            ((TileEntityStationNameRedstone)tile).onRedstoneUpdate(world.isBlockIndirectlyGettingPowered(x, y, z));
    }
}
