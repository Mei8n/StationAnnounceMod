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

        // RTMのバールを持っているか判定
        if (heldItem != null && RTMCompat.isCrowbar(heldItem)) {
            this.renderBeacon(x, y, z);
        }
    }

    private void renderBeacon(double x, double y, double z) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glPushMatrix();
        // ブロックの中心に座標を移動
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // 加算合成

        // ビームの色設定 (IFTTT風のスカイブルー)
        GL11.glColor4f(0.0F, 0.6F, 1.0F, 0.5F);

        double size = 0.15; // ビームの太さ
        double height = 256.0; // 空高く伸ばす

        tessellator.startDrawingQuads();
        // 前面
        tessellator.addVertex(-size, 0, -size);
        tessellator.addVertex(-size, height, -size);
        tessellator.addVertex(size, height, -size);
        tessellator.addVertex(size, 0, -size);
        // 背面
        tessellator.addVertex(-size, 0, size);
        tessellator.addVertex(size, 0, size);
        tessellator.addVertex(size, height, size);
        tessellator.addVertex(-size, height, size);
        // 左面
        tessellator.addVertex(-size, 0, -size);
        tessellator.addVertex(-size, 0, size);
        tessellator.addVertex(-size, height, size);
        tessellator.addVertex(-size, height, -size);
        // 右面
        tessellator.addVertex(size, 0, -size);
        tessellator.addVertex(size, height, -size);
        tessellator.addVertex(size, height, size);
        tessellator.addVertex(size, 0, size);

        tessellator.draw();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }
}
