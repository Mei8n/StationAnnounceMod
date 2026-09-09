package jp.me1han.sam;

import jp.me1han.sam.render.TileEntityDepartureMelody;
import jp.me1han.sam.render.TileEntityDepartureSwitch;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import jp.me1han.sam.link.SamLinkRegistry;

public final class DepartureSwitchLink {
    public static boolean isSwitch(TileEntity tile) { return tile instanceof TileEntityDepartureSwitch; }
    public static String getKey(TileEntity tile) {
        return isSwitch(tile) ? TileEntityDepartureMelody.normalize(((TileEntityDepartureSwitch) tile).linkKey) : "";
    }
    public static TileEntityDepartureMelody findDevice(TileEntity source) {
        String key = getKey(source);
        if (key.isEmpty() || source.getWorldObj() == null) return null;
        java.util.List<TileEntityDepartureMelody> devices =
            SamLinkRegistry.findAll(source.getWorldObj(), key, TileEntityDepartureMelody.class);
        return devices.size() == 1 ? devices.get(0) : null;
    }
    public static void click(TileEntity source, EntityPlayer player) {
        TileEntityDepartureMelody device = findDevice(source);
        if (device == null) player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("message.sam.departure.no_device"));
        else if (!device.click(source)) player.addChatMessage(new ChatComponentText("[SAM] " + device.lastError));
    }
}
