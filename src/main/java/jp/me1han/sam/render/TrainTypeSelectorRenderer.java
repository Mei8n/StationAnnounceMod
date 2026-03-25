package jp.me1han.sam.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public class TrainTypeSelectorRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float f) {
        ItemStack heldItem = Minecraft.getMinecraft().thePlayer.getHeldItem();

        if (heldItem != null && isCrowbar(heldItem)) {
            renderBeaconBeam(x, y, z, f);
        }
    }

    private boolean isCrowbar(ItemStack stack) {
        return stack.getItem().getClass().getName().contains("ItemCrowbar");
    }

    private void renderBeaconBeam(double x, double y, double z, float partialTicks) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 1.0, z + 0.5);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        long time = Minecraft.getMinecraft().theWorld.getTotalWorldTime();
        float f1 = MathHelper.sin((float)time * 0.2F) * 0.1F + 0.5F;

        tessellator.startDrawingQuads();
        tessellator.setColorRGBA(255, 0, 20, 150);

        double size = 0.15;
        double height = 256.0; // 空高く飛ばす

        tessellator.addVertex(size, height, size);
        tessellator.addVertex(size, 0, size);
        tessellator.addVertex(-size, 0, size);
        tessellator.addVertex(-size, height, size);

        tessellator.addVertex(-size, height, -size);
        tessellator.addVertex(-size, 0, -size);
        tessellator.addVertex(size, 0, -size);
        tessellator.addVertex(size, height, -size);

        tessellator.addVertex(size, height, -size);
        tessellator.addVertex(size, 0, -size);
        tessellator.addVertex(size, 0, size);
        tessellator.addVertex(size, height, size);

        tessellator.addVertex(-size, height, size);
        tessellator.addVertex(-size, 0, size);
        tessellator.addVertex(-size, 0, -size);
        tessellator.addVertex(-size, height, -size);

        tessellator.draw();

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }
}
