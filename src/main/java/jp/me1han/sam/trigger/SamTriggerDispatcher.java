package jp.me1han.sam.trigger;

import jp.me1han.sam.link.LinkKey;
import jp.me1han.sam.link.SamLinkRegistry;
import jp.me1han.sam.network.PacketAnnounce;
import jp.me1han.sam.network.ServerSessions;
import jp.me1han.sam.render.TileEntityAnnouncer;
import jp.me1han.sam.render.TileEntityAwarenessAnnouncer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Lightweight synchronous dispatcher for internal, server-side triggers. */
public final class SamTriggerDispatcher {
    private SamTriggerDispatcher() {}

    public static void dispatch(World world, SamTrigger trigger) {
        if (world == null || world.isRemote || trigger == null || LinkKey.isEmpty(trigger.linkKey)) return;
        switch (trigger.type) {
            case ANNOUNCE_START:
                TileEntityAnnouncer target = SamLinkRegistry.findFirst(world, trigger.linkKey, TileEntityAnnouncer.class);
                if (target != null) target.startAnnounce();
                break;
            case ANNOUNCE_STOP:
                TileEntityAnnouncer owner = SamLinkRegistry.findFirst(world, trigger.linkKey, TileEntityAnnouncer.class);
                if (owner != null) owner.forceStop();
                break;
            case DEPARTURE_FINISHED:
                boolean scheduled = false;
                for (TileEntityAwarenessAnnouncer awareness :
                     SamLinkRegistry.findAll(world, trigger.linkKey, TileEntityAwarenessAnnouncer.class)) {
                    if (awareness.playAfterDeparture) {
                        awareness.scheduleAfterDeparture();
                        scheduled = true;
                    }
                }
                if (scheduled) {
                    TileEntity source = SamLinkRegistry.at(world, trigger.sourceX, trigger.sourceY, trigger.sourceZ);
                    if (source instanceof TileEntityAnnouncer) {
                        ServerSessions.stopPriority((TileEntityAnnouncer) source, PacketAnnounce.PRIORITY_AWARENESS);
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported SAM trigger: " + trigger.type);
        }
    }
}
