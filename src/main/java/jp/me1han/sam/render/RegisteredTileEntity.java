package jp.me1han.sam.render;

import jp.me1han.sam.LoadedSamTiles;
import jp.me1han.sam.link.SamLinkRegistry;
import net.minecraft.tileentity.TileEntity;

public abstract class RegisteredTileEntity extends TileEntity {
    @Override public void validate() { super.validate(); LoadedSamTiles.register(this); SamLinkRegistry.register(this); }
    @Override public void invalidate() { SamLinkRegistry.unregister(this); LoadedSamTiles.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { SamLinkRegistry.unregister(this); LoadedSamTiles.unregister(this); super.onChunkUnload(); }
}
