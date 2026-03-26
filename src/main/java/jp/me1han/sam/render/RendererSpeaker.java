package jp.me1han.sam.render;

import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.client.SAMRendererBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public class RendererSpeaker extends SAMRendererBase {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntitySpeaker)) return;
        TileEntitySpeaker speaker = (TileEntitySpeaker) te;

        if (!isPlayerHoldingDebugItem()) return;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        GL11.glDisable(GL11.GL_LIGHTING);   // 影を無効化
        GL11.glEnable(GL11.GL_BLEND);       // 半透明有効
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0.0F, 1.0F, 1.0F, 0.7F);

        AxisAlignedBB blockAabb = AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1);
        RenderGlobal.drawOutlinedBoundingBox(blockAabb, -1);

        GL11.glLineWidth(1.0F);

        float radius = (float) speaker.range;
        if (radius > 0.1F) {
            GL11.glPushMatrix();
            GL11.glTranslated(0.5, 0.5, 0.5);

            GL11.glColor4f(0.2F, 0.8F, 1.0F, 0.2F);

            drawSphere(radius, 24, 24);

            GL11.glPopMatrix();
        }

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();

        this.renderCustomBeam(te, x, y, z, partialTicks);
    }

    private boolean isPlayerHoldingDebugItem() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return false;

        if (isPlayerHoldingRTM_Crowbar()) return true;

        Item speakerItem = Item.getItemFromBlock(StationAnnounceModCore.blockSpeaker);
        return held.getItem() == speakerItem;
    }

    private void drawSphere(float radius, int rings, int sectors) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        float pi = (float) Math.PI;
        float halfPi = pi / 2.0f;

        for (int r = 0; r < rings; r++) {
            float phi1 = -halfPi + pi * (float) r / rings;
            float phi2 = -halfPi + pi * (float) (r + 1) / rings;

            for (int s = 0; s < sectors; s++) {
                float theta1 = 2.0f * pi * (float) s / sectors;
                float theta2 = 2.0f * pi * (float) (s + 1) / sectors;

                addVertex(tessellator, radius, phi1, theta1);
                addVertex(tessellator, radius, phi2, theta1);
                addVertex(tessellator, radius, phi2, theta2);
                addVertex(tessellator, radius, phi1, theta2);
            }
        }
        tessellator.draw();
    }

    private void addVertex(Tessellator tess, float r, float phi, float theta) {
        float x = r * MathHelper.cos(phi) * MathHelper.cos(theta);
        float y = r * MathHelper.sin(phi);
        float z = r * MathHelper.cos(phi) * MathHelper.sin(theta);
        tess.addVertex(x, y, z);
    }
}
