package jp.me1han.sam.block;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.compat.TrainCompatRegistry;
import jp.me1han.sam.render.TileEntityStationNameStopDetector;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class BlockStationNameStopDetector extends Block implements ITileEntityProvider {
    public BlockStationNameStopDetector() {
        super(Material.iron);
        setBlockName("sam.station_name_stop_detector");
        setBlockTextureName("stationannouncemod:station_name_stop_detector");
        if (TrainCompatRegistry.get().isAvailable()) setCreativeTab(StationAnnounceModCore.tabSAM);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileEntityStationNameStopDetector(); }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
            int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) player.openGui(StationAnnounceModCore.instance,
            StationAnnounceModCore.GUI_ID_STATION_NAME, world, x, y, z);
        return true;
    }
}
