package jp.me1han.sam.client;

import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

public abstract class SAMRendererBase extends TileEntitySpecialRenderer {

    protected boolean isPlayerHoldingRTM_Crowbar() {
        if (!Loader.isModLoaded("RTM")) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null) return false;

        // クラス名に ItemCrowbar が含まれるかチェック（ATSAssistMod等の仕様に準拠）
        return heldItem.getItem().getClass().getName().contains("ItemCrowbar");
    }

    protected void renderCustomBeam(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!isPlayerHoldingRTM_Crowbar()) return;

        Tessellator tessellator = Tessellator.instance;
        GL11.glPushMatrix();

        // ブロックの中心直上から描画
        GL11.glTranslated(x + 0.5, y + 1.0, z + 0.5);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        // 加算合成（IFTTTブロックのような発光感）
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // 仕様準拠：太さは 0.15 固定（アニメーションなし）
        double size = 0.15;
        double height = 256.0;

        tessellator.startDrawingQuads();
        // マゼンタ（赤紫色）
        tessellator.setColorRGBA(255, 0, 255, 150);

        // --- 四方の面を描画 ---
        // 南面
        tessellator.addVertex(size, height, size);
        tessellator.addVertex(size, 0, size);
        tessellator.addVertex(-size, 0, size);
        tessellator.addVertex(-size, height, size);
        // 北面
        tessellator.addVertex(-size, height, -size);
        tessellator.addVertex(-size, 0, -size);
        tessellator.addVertex(size, 0, -size);
        tessellator.addVertex(size, height, -size);
        // 東面
        tessellator.addVertex(size, height, -size);
        tessellator.addVertex(size, 0, -size);
        tessellator.addVertex(size, 0, size);
        tessellator.addVertex(size, height, size);
        // 西面
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
