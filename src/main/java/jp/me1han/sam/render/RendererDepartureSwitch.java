package jp.me1han.sam.render;

import jp.me1han.sam.client.SwitchMeshRenderer;
import jp.me1han.sam.switchmodel.SwitchModelDefinition;
import jp.me1han.sam.switchmodel.SwitchModelRegistry;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

public class RendererDepartureSwitch extends TileEntitySpecialRenderer {
    @Override public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partial) {
        TileEntityDepartureSwitch button = (TileEntityDepartureSwitch) tile;
        SwitchModelDefinition definition = SwitchModelRegistry.getOrDefault(button.modelName);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + 0.5, y, z + 0.5);
            GL11.glTranslatef(button.getOffsetX(), button.getOffsetY(), button.getOffsetZ());
            GL11.glRotatef(button.getRotationYaw(), 0, 1, 0);
            if (!SwitchMeshRenderer.INSTANCE.render(definition, button.isActivated(), tile.getWorldObj().getLightBrightnessForSkyBlocks(tile.xCoord, tile.yCoord, tile.zCoord, 0))) {
                SwitchMeshRenderer.INSTANCE.render(SwitchModelRegistry.get(SwitchModelRegistry.DEFAULT_MODEL), false, 0xF000F0);
            }
        } finally { GL11.glPopMatrix(); }
    }
}
