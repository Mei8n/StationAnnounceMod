package jp.me1han.sam.block;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.TileEntitySpeaker;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase; // 必要
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;         // 必要
import net.minecraft.nbt.NBTTagCompound;     // 必要
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockSpeaker extends BlockContainer {
    public BlockSpeaker() {
        super(Material.circuits);
        this.setBlockName("sam.speaker");
        this.setBlockTextureName("stationannouncemod:speaker");
        this.setCreativeTab(StationAnnounceModCore.tabSAM);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hX, float hY, float hZ) {
        if (!world.isRemote) {
            player.openGui(StationAnnounceModCore.instance, StationAnnounceModCore.GUI_ID_SPEAKER, world, x, y, z);
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, net.minecraft.entity.EntityLivingBase entity, ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("BlockEntityTag")) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntitySpeaker) {
                NBTTagCompound nbt = stack.getTagCompound().getCompoundTag("BlockEntityTag");

                nbt.setInteger("x", x);
                nbt.setInteger("y", y);
                nbt.setInteger("z", z);

                te.readFromNBT(nbt);
                te.markDirty();
                world.markBlockForUpdate(x, y, z);
            }
        }
    }

    @Override
    public boolean isOpaqueCube() { return false; }
    @Override
    public boolean renderAsNormalBlock() { return false; }
    @Override
    public int getRenderType() { return -1; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntitySpeaker();
    }
}
