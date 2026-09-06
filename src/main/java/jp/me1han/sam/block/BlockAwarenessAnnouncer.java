package jp.me1han.sam.block;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.TileEntityAwarenessAnnouncer;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockAwarenessAnnouncer extends Block implements ITileEntityProvider {
    public BlockAwarenessAnnouncer() {
        super(Material.iron);
        this.setBlockName("sam.awareness_announcer");
        this.setBlockTextureName("stationannouncemod:awareness_announcer");
        this.setCreativeTab(StationAnnounceModCore.tabSAM);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(StationAnnounceModCore.instance, StationAnnounceModCore.GUI_ID_AWARENESS_ANNOUNCER, world, x, y, z);
        }
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityAwarenessAnnouncer();
    }
}
