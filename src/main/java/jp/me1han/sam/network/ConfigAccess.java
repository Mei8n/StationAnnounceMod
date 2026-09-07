package jp.me1han.sam.network;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound;
import java.util.function.Consumer;

/** All GUI writes pass through this server-thread access check, before touching a TE. */
public final class ConfigAccess {
    public static <T extends TileEntity> void enqueue(MessageContext ctx, int x, int y, int z,
                                                     Class<T> type, Consumer<T> apply) {
        final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        // Capture identity only. World/TE operations run inside the queue.
        final World expectedWorld = player.worldObj;
        final net.minecraft.network.NetHandlerPlayServer connection = ctx.getServerHandler();
        ServerTaskQueue.INSTANCE.enqueue(() -> {
            if (player.isDead || player.worldObj == null || player.worldObj != expectedWorld
                || player.worldObj.isRemote || player.playerNetServerHandler != connection
                || !connection.netManager.isChannelOpen()) return;
            World world = player.worldObj;
            if (!world.blockExists(x, y, z)) return;
            TileEntity tile = world.getTileEntity(x, y, z);
            if (!type.isInstance(tile) || tile.isInvalid() || tile.getWorldObj() != world
                || player.getDistanceSq(x+.5, y+.5, z+.5) > 64
                || !player.canPlayerEdit(x, y, z, 1, player.getHeldItem())
                || !world.canMineBlock(player, x, y, z)) return;
            apply.accept(type.cast(tile));
        });
    }
    public static boolean key(String key) { return PacketLimits.string(key, PacketLimits.LINK_KEY); }
    public static String normalize(String value) { return value == null ? "" : value.trim(); }
    /** Simple field-only changes: unchanged data emits no dirty/update event. */
    public static void change(TileEntity tile, Runnable change) {
        NBTTagCompound before = new NBTTagCompound(); tile.writeToNBT(before);
        change.run();
        NBTTagCompound after = new NBTTagCompound(); tile.writeToNBT(after);
        if (before.equals(after)) return;
        tile.markDirty();
        tile.getWorldObj().markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
    }
}
