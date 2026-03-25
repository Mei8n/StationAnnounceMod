package jp.me1han.sam.block;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.TileEntityTrainTypeSelector;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.List;

public class BlockTrainTypeSelector extends Block implements ITileEntityProvider {

    public BlockTrainTypeSelector() {
        super(Material.iron);
        this.setBlockName("trainTypeSelector");
        this.setBlockTextureName("stationannouncemod:train_type_selector");
        this.setHardness(2.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeMetal);
        this.setCreativeTab(StationAnnounceModCore.tabSAM);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        if (Loader.isModLoaded("RTM")) {
            list.add(new net.minecraft.item.ItemStack(item));
        }
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityTrainTypeSelector();
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        super.breakBlock(world, x, y, z, block, meta);
        world.removeTileEntity(x, y, z);
    }

    @Override
    public boolean onBlockEventReceived(World world, int x, int y, int z, int id, int param) {
        super.onBlockEventReceived(world, x, y, z, id, param);
        TileEntity tileentity = world.getTileEntity(x, y, z);
        return tileentity != null && tileentity.receiveClientEvent(id, param);
    }
}
