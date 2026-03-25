package jp.me1han.sam.network;

import cpw.mods.fml.common.network.IGuiHandler;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.container.ContainerDebugReceiver;
import jp.me1han.sam.gui.GuiAnnouncer;
import jp.me1han.sam.gui.GuiDebugReceiver;
import jp.me1han.sam.gui.GuiTrainTypeSelector;
import jp.me1han.sam.render.TileEntityAnnouncer;
import jp.me1han.sam.render.TileEntityDebugReceiver;
import jp.me1han.sam.render.TileEntityTrainTypeSelector;
import jp.me1han.sam.container.ContainerAnnouncer;
import jp.me1han.sam.container.ContainerTrainTypeSelector;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class SAMGuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);

        if (ID == StationAnnounceModCore.GUI_ID_ANNOUNCER) {
            if (tile instanceof TileEntityAnnouncer) {
                return new ContainerAnnouncer((TileEntityAnnouncer) tile);
            }
        }

        if (ID == StationAnnounceModCore.GUI_ID_TRAIN_TYPE_SELECTOR) {
            if (tile instanceof TileEntityTrainTypeSelector) {
                return new ContainerTrainTypeSelector((TileEntityTrainTypeSelector) tile);
            }
        }

        if (ID == StationAnnounceModCore.GUI_ID_DEBUG_RECEIVER) {
            if (tile instanceof TileEntityDebugReceiver) {
                return new ContainerDebugReceiver((TileEntityDebugReceiver) tile);
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);

        if (ID == StationAnnounceModCore.GUI_ID_ANNOUNCER) {
            if (tile instanceof TileEntityAnnouncer) {
                return new GuiAnnouncer(new ContainerAnnouncer((TileEntityAnnouncer) tile), (TileEntityAnnouncer) tile);
            }
        }

        if (ID == StationAnnounceModCore.GUI_ID_TRAIN_TYPE_SELECTOR) {
            if (tile instanceof TileEntityTrainTypeSelector) {
                return new GuiTrainTypeSelector(new ContainerTrainTypeSelector((TileEntityTrainTypeSelector) tile), (TileEntityTrainTypeSelector) tile);
            }
        }

        if (ID == StationAnnounceModCore.GUI_ID_DEBUG_RECEIVER) {
            if (tile instanceof TileEntityDebugReceiver) {
                return new GuiDebugReceiver((TileEntityDebugReceiver) tile);
            }
        }
        return null;
    }
}
