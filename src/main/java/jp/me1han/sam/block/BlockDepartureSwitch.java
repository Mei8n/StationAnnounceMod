package jp.me1han.sam.block;

import jp.me1han.sam.DepartureSwitchLink;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.render.TileEntityDepartureSwitch;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockDepartureSwitch extends Block implements ITileEntityProvider {
    public BlockDepartureSwitch() {
        super(Material.iron);
        setBlockName("sam.departure_switch");
        setBlockTextureName("minecraft:iron_block");
        setCreativeTab(StationAnnounceModCore.tabSAM);
        setHardness(1.0F);
        setBlockBounds(0.25F, 0, 0.25F, 0.75F, 0.25F, 0.75F);
    }
    @Override public boolean isOpaqueCube() { return false; }
    @Override public boolean renderAsNormalBlock() { return false; }
    @Override public int getRenderType() { return -1; }
    @Override public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        jp.me1han.sam.switchmodel.SwitchModelDefinition model = tile instanceof TileEntityDepartureSwitch
            ? jp.me1han.sam.switchmodel.SwitchModelRegistry.getOrDefault(((TileEntityDepartureSwitch) tile).modelName) : null;
        if (model == null) { setBlockBounds(0.25F, 0, 0.25F, 0.75F, 0.3F, 0.75F); return; }
        double[] b = jp.me1han.sam.switchmodel.SwitchYaw.rotateBounds(model.bounds,
            ((TileEntityDepartureSwitch) tile).getRotationYaw());
        setBlockBounds((float) b[0], (float) b[1], (float) b[2], (float) b[3], (float) b[4], (float) b[5]);
    }
    @Override public void onBlockPlacedBy(World world, int x, int y, int z, net.minecraft.entity.EntityLivingBase placer, net.minecraft.item.ItemStack stack) {
        if (world.isRemote) return;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityDepartureSwitch) {
            TileEntityDepartureSwitch button = (TileEntityDepartureSwitch) tile;
            if (stack.hasTagCompound() && stack.getTagCompound().hasKey("BlockEntityTag")) {
                button.readSettings(stack.getTagCompound().getCompoundTag("BlockEntityTag"));
            }
            // Placement orientation overrides any angle copied with the item settings.
            button.setRotationYaw(jp.me1han.sam.switchmodel.SwitchYaw.placement(placer.rotationYaw, placer.isSneaking()));
            button.resetState(); // Marks dirty and sends the server description packet.
        }
    }
    @Override public TileEntity createNewTileEntity(World world, int metadata) { return new TileEntityDepartureSwitch(); }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (player.isSneaking()) {
                player.openGui(StationAnnounceModCore.instance, StationAnnounceModCore.GUI_ID_DEPARTURE_SWITCH, world, x, y, z);
            } else if (!DepartureSwitchLink.getKey(tile).isEmpty()) {
                DepartureSwitchLink.click(tile, player);
            }
        }
        return true;
    }
}
