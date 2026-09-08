package jp.me1han.sam.render;

import jp.me1han.sam.LoadedSamTiles;
import net.minecraft.tileentity.TileEntity;

public abstract class RegisteredTileEntity extends TileEntity {
    @Override public void validate() { super.validate(); LoadedSamTiles.register(this); }
    @Override public void invalidate() { LoadedSamTiles.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { LoadedSamTiles.unregister(this); super.onChunkUnload(); }
}
