package jp.me1han.sam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

public class TrainSelectorRenderer extends TileEntitySpecialRenderer {
    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float f) {
        ItemStack heldItem = Minecraft.getMinecraft().thePlayer.getHeldItem();

        if (heldItem != null && RTMCompat.isCrowbar(heldItem)) {
            renderBeaconBeam(x, y, z);
        }
    }

    private void renderBeaconBeam(double x, double y, double z) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y, z + 0.5); // ブロックの中心から
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // 加算合成で光らせる

        // ビームの描画 (IFTTTブロック風の青白い光)
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA(0, 150, 255, 100);
        double r = 0.2; // ビームの太さ
        double h = 5.0; // ビームの高さ

        // 四角柱を描画
        tessellator.addVertex(-r, 0, -r);
        tessellator.addVertex(-r, h, -r);
        tessellator.addVertex(r, h, -r);
        tessellator.addVertex(r, 0, -r);
        // (他の面も同様に記述...)

        tessellator.draw();

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }
}
