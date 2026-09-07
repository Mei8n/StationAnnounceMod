package jp.me1han.sam;

import jp.me1han.sam.render.TileEntityDepartureMelody;
import jp.me1han.sam.render.TileEntityDepartureSwitch;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;

public final class DepartureSwitchLink {
    public static boolean isSwitch(TileEntity tile) { return tile instanceof TileEntityDepartureSwitch; }
    public static String getKey(TileEntity tile) {
        return isSwitch(tile) ? TileEntityDepartureMelody.normalize(((TileEntityDepartureSwitch) tile).linkKey) : "";
    }
    public static TileEntityDepartureMelody findDevice(TileEntity source) {
        String key = getKey(source);
        if (key.isEmpty() || source.getWorldObj() == null) return null;
        TileEntityDepartureMelody result = null;
        for (Object obj : source.getWorldObj().loadedTileEntityList) {
            if (obj instanceof TileEntityDepartureMelody && !((TileEntity) obj).isInvalid()) {
                TileEntityDepartureMelody device = (TileEntityDepartureMelody) obj;
                if (key.equals(TileEntityDepartureMelody.normalize(device.linkKey))) {
                    if (result != null) return null;
                    result = device;
                }
            }
        }
        return result;
    }
    public static void click(TileEntity source, EntityPlayer player) {
        TileEntityDepartureMelody device = findDevice(source);
        if (device == null) player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("message.sam.departure.no_device"));
        else if (!device.click(source)) player.addChatMessage(new ChatComponentText("[SAM] " + device.lastError));
    }
}
