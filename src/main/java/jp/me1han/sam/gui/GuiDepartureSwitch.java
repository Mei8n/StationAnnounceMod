package jp.me1han.sam.gui;

import java.util.*;
import jp.me1han.sam.client.SwitchMeshRenderer;
import jp.me1han.sam.network.*;
import jp.me1han.sam.render.TileEntityDepartureSwitch;
import jp.me1han.sam.switchmodel.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GuiDepartureSwitch extends GuiScreen {
    private final TileEntityDepartureSwitch tile;
    private GuiTextField key, search, rotationField;
    private String selected;
    private int scroll;
    private boolean pressedPreview;
    private int left, listWidth, right, rightWidth, top, bottom;
    private final List<SwitchModelDefinition> filtered = new ArrayList<>();

    public GuiDepartureSwitch(TileEntity tile) {
        this.tile = (TileEntityDepartureSwitch) tile;
        selected = this.tile.modelName;
    }
    @Override public void initGui() {
        String keyText = key == null ? tile.linkKey : key.getText();
        String searchText = search == null ? "" : search.getText();
        String rotationText = rotationField == null ? currentRotationText() : rotationField.getText();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        left = 12; listWidth = width / 2 - 20; right = width / 2 + 8; rightWidth = width - right - 12;
        top = 64; bottom = height - 38;
        search = new GuiTextField(fontRendererObj, left, 36, listWidth, 20);
        search.setMaxStringLength(128); search.setText(searchText);
        key = new GuiTextField(fontRendererObj, right, 36, rightWidth, 20);
        key.setMaxStringLength(64); key.setText(keyText);
        buttonList.add(new GuiButton(0, width - 112, height - 28, 100, 20, I18n.format("gui.done")));
        buttonList.add(new GuiButton(1, width - 220, height - 28, 100, 20, I18n.format("gui.cancel")));
        int labelWidth = fontRendererObj.getStringWidth(I18n.format("gui.sam.switch.rotation") + ": ");
        rotationField = new GuiTextField(fontRendererObj, right + labelWidth, 64, rightWidth - labelWidth - 14, 20);
        rotationField.setMaxStringLength(11);
        rotationField.setText(rotationText);
        buttonList.add(new GuiButton(3, right, 89, rightWidth, 20, previewText()));
        filter();
    }
    private String currentRotationText() { return Integer.toString(Math.round(tile.getRotationYaw())); }
    private String previewText() { return I18n.format("gui.sam.switch.preview") + ": " + I18n.format(pressedPreview ? "gui.sam.switch.pressed" : "gui.sam.switch.normal"); }
    private int rows() { return Math.max(1, (bottom - top) / 24); }
    private void filter() {
        filtered.clear();
        String query = search.getText().toLowerCase(Locale.ROOT);
        for (SwitchModelDefinition model : SwitchModelRegistry.list()) {
            if ((model.name + " " + model.displayName + " " + model.tags).toLowerCase(Locale.ROOT).contains(query)) filtered.add(model);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - rows())));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0 && SwitchModelRegistry.get(selected) != null) {
            final int yaw;
            try { yaw = SwitchYaw.parse(rotationField.getText()); }
            catch (NumberFormatException e) {
                rotationField.setText(currentRotationText());
                return;
            }
            NetworkHandler.INSTANCE.sendToServer(new PacketDepartureSwitchConfig(tile.xCoord, tile.yCoord, tile.zCoord, key.getText().trim(), selected, yaw));
            mc.thePlayer.closeScreen();
        } else if (button.id == 1) mc.thePlayer.closeScreen();
        else if (button.id == 3) { pressedPreview = !pressedPreview; button.displayString = previewText(); }
    }
    @Override public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("gui.sam.departure.switch_title"), width / 2, 8, 0xFFFFFF);
        drawString(fontRendererObj, I18n.format("gui.sam.switch.search"), left, 25, 0xAAAAAA);
        drawString(fontRendererObj, I18n.format("gui.sam.link_key"), right, 25, 0xAAAAAA);
        search.drawTextBox(); key.drawTextBox();
        drawString(fontRendererObj, I18n.format("gui.sam.switch.rotation") + ":", right, 70, 0xAAAAAA);
        drawString(fontRendererObj, "°", right + rightWidth - 10, 70, 0xAAAAAA);
        rotationField.drawTextBox();
        for (int row = 0; row < rows() && row + scroll < filtered.size(); row++) {
            SwitchModelDefinition model = filtered.get(row + scroll);
            int y = top + row * 24;
            boolean hover = mx >= left && mx < left + listWidth && my >= y && my < y + 24;
            drawRect(left, y, left + listWidth, y + 23, model.name.equals(selected) ? 0xFF365C78 : hover ? 0xFF444444 : 0xBB202020);
            int reserve = model.buttonTexture.isEmpty() ? 8 : 32;
            String text = fontRendererObj.trimStringToWidth(model.displayName, listWidth - reserve);
            drawString(fontRendererObj, text, left + 4, y + 8, 0xFFFFFF);
            if (!model.buttonTexture.isEmpty()) {
                mc.getTextureManager().bindTexture(new ResourceLocation(model.buttonTexture));
                GL11.glColor4f(1, 1, 1, 1);
                texture(left + 8 + fontRendererObj.getStringWidth(text), y + 3, 18, 18);
            }
        }
        if (filtered.isEmpty()) drawString(fontRendererObj, I18n.format("gui.sam.switch.empty"), left + 4, top + 6, 0xAAAAAA);
        if (filtered.size() > rows()) {
            int bar = Math.max(8, (bottom - top) * rows() / filtered.size());
            int y = top + (bottom - top - bar) * scroll / (filtered.size() - rows());
            drawRect(left + listWidth - 2, y, left + listWidth, y + bar, 0xFFAAAAAA);
        }
        SwitchModelDefinition model = SwitchModelRegistry.get(selected);
        ((GuiButton) buttonList.get(0)).enabled = model != null;
        if (model == null) {
            fontRendererObj.drawSplitString(I18n.format("gui.sam.switch.missing") + ": " + selected, right, 120, rightWidth, 0xFF7777);
        } else {
            renderPreview(model);
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(model.name, rightWidth), right, bottom - 12, 0xAAAAAA);
        }
        super.drawScreen(mx, my, partial);
    }
    private void renderPreview(SwitchModelDefinition model) {
        MqoMesh mesh = SwitchMeshRenderer.INSTANCE.mesh(model);
        if (mesh == null) {
            fontRendererObj.drawSplitString(I18n.format("gui.sam.switch.model_error"), right, 120, rightWidth, 0xFF7777);
            return;
        }
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Map.Entry<String, List<MqoMesh.Triangle>> part : mesh.parts.entrySet()) {
            // Fit both states so preview switching does not change the zoom.
            for (boolean state : new boolean[]{false, true}) {
                double[] shift = model.offset(part.getKey(), state);
                for (MqoMesh.Triangle face : part.getValue()) for (double[] vertex : face.vertices) for (int i = 0; i < 3; i++) {
                    double v = (vertex[i] + model.modelOffset[i] + shift[i]) * model.scale;
                    min[i] = Math.min(min[i], v); max[i] = Math.max(max[i], v);
                }
            }
        }
        double extent = Math.max(0.01, Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2])));
        int previewHeight = Math.max(20, bottom - 138);
        double zoom = Math.min(rightWidth - 16, previewHeight) / (extent * 1.8);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glTranslated(right + rightWidth / 2.0, 118 + previewHeight / 2.0, 100);
            GL11.glScaled(zoom, -zoom, zoom);
            GL11.glRotatef(22, 1, 0, 0);
            float previewYaw = tile.getRotationYaw();
            try { previewYaw = SwitchYaw.parse(rotationField.getText()); }
            catch (NumberFormatException ignored) { }
            GL11.glRotatef(35 + previewYaw, 0, 1, 0);
            GL11.glTranslated(-(min[0] + max[0]) / 2, -(min[1] + max[1]) / 2, -(min[2] + max[2]) / 2);
            SwitchMeshRenderer.INSTANCE.render(model, pressedPreview, 0xF000F0);
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
    private void texture(int x, int y, int w, int h) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + h, 0, 0, 1); tess.addVertexWithUV(x + w, y + h, 0, 1, 1);
        tess.addVertexWithUV(x + w, y, 0, 1, 0); tess.addVertexWithUV(x, y, 0, 0, 0);
        tess.draw();
    }
    @Override public void handleMouseInput() {
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (delta != 0 && x >= left && x < left + listWidth && y >= top && y < bottom) {
            scroll += delta > 0 ? -1 : 1; filter();
        }
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        key.mouseClicked(x, y, button); search.mouseClicked(x, y, button);
        rotationField.mouseClicked(x, y, button);
        int row = (y - top) / 24 + scroll;
        if (button == 0 && x >= left && x < left + listWidth && y >= top && y < top + rows() * 24 && row < filtered.size()) selected = filtered.get(row).name;
    }
    @Override protected void keyTyped(char c, int code) {
        if (rotationField.isFocused()) {
            if (code == Keyboard.KEY_ESCAPE) super.keyTyped(c, code);
            else rotationField.textboxKeyTyped(c, code);
            return;
        }
        if (search.textboxKeyTyped(c, code)) { scroll = 0; filter(); }
        else if (!key.textboxKeyTyped(c, code)) super.keyTyped(c, code);
    }
    @Override public void updateScreen() { key.updateCursorCounter(); search.updateCursorCounter(); rotationField.updateCursorCounter(); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
