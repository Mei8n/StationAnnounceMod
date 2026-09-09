package jp.me1han.sam.trigger;

import jp.me1han.sam.link.LinkKey;
import net.minecraft.tileentity.TileEntity;

/** Small immutable description of a synchronous server-side SAM trigger. */
public final class SamTrigger {
    public static final long NO_FORMATION = -1L;

    public final SamTriggerType type;
    public final String linkKey;
    public final int sourceX;
    public final int sourceY;
    public final int sourceZ;
    public final SamTriggerSourceType sourceType;
    public final long formationId;

    public SamTrigger(SamTriggerType type, String linkKey, int sourceX, int sourceY, int sourceZ,
                      SamTriggerSourceType sourceType, long formationId) {
        if (type == null || sourceType == null) throw new IllegalArgumentException("Trigger type and source type are required");
        this.type = type;
        this.linkKey = LinkKey.normalize(linkKey);
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceZ = sourceZ;
        this.sourceType = sourceType;
        this.formationId = formationId;
    }

    public static SamTrigger from(TileEntity source, SamTriggerType type, String linkKey,
                                  SamTriggerSourceType sourceType, long formationId) {
        return new SamTrigger(type, linkKey, source.xCoord, source.yCoord, source.zCoord, sourceType, formationId);
    }
}
