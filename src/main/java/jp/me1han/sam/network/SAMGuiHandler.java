package jp.me1han.sam.network;

import cpw.mods.fml.common.network.IGuiHandler;
import jp.me1han.sam.gui.GuiAnnouncer;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class SAMGuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        // サーバー側にはContainer（インベントリ）がないのでnull
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);

        // ID 0 の時に GuiAnnouncer を開く
        if (ID == 0 && te instanceof TileEntityAnnouncer) {
            return new GuiAnnouncer((TileEntityAnnouncer) te);
        }
        return null;
    }
}
