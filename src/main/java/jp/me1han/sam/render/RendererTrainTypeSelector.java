package jp.me1han.sam.render;

import jp.me1han.sam.client.SAMRendererBase;
import net.minecraft.tileentity.TileEntity;

public class RendererTrainTypeSelector extends SAMRendererBase {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        this.renderCustomBeam(te, x, y, z, partialTicks);
    }
}
