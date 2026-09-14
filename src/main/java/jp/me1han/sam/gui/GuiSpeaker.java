package jp.me1han.sam.gui;

import java.util.*;
import jp.me1han.sam.client.SwitchMeshRenderer;
import jp.me1han.sam.network.*;
import jp.me1han.sam.render.TileEntitySpeaker;
import jp.me1han.sam.speakermodel.*;
import jp.me1han.sam.switchmodel.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Speaker audio configuration and optional pack model selection. */
public class GuiSpeaker extends GuiScreen {
    private final TileEntitySpeaker tile;
    private GuiTextField key, range, volume, search, rotation, offsetX, offsetY, offsetZ;
    private String selected;
    private int scroll, left, listWidth, right, rightWidth, top, bottom;
    private final List<String> filtered = new ArrayList<>();

    public GuiSpeaker(TileEntitySpeaker tile) { this.tile = tile; this.selected = tile.modelName; }

    @Override public void initGui() {
        String keyText = key == null ? tile.linkKey : key.getText();
        String rangeText = range == null ? Integer.toString(tile.range) : range.getText();
        String volumeText = volume == null ? Float.toString(tile.volume) : volume.getText();
        String searchText = search == null ? "" : search.getText();
        String rotationText = rotation == null ? Integer.toString(Math.round(tile.getRotationYaw())) : rotation.getText();
        String xText = offsetX == null ? Float.toString(tile.getOffsetX()) : offsetX.getText();
        String yText = offsetY == null ? Float.toString(tile.getOffsetY()) : offsetY.getText();
        String zText = offsetZ == null ? Float.toString(tile.getOffsetZ()) : offsetZ.getText();
        Keyboard.enableRepeatEvents(true); buttonList.clear();
        left = 12; listWidth = width / 2 - 20; right = width / 2 + 8; rightWidth = width - right - 12;
        top = 56; bottom = height - 36;
        search = field(left, 30, listWidth, PacketLimits.MODEL, searchText);
        key = field(right, 30, rightWidth, PacketLimits.LINK_KEY, keyText);
        int half = Math.max(28, (rightWidth - 4) / 2);
        range = field(right, 65, half, 10, rangeText);
        volume = field(right + half + 4, 65, rightWidth - half - 4, 16, volumeText);
        int rotationLabel = fontRendererObj.getStringWidth(I18n.format("gui.sam.speaker.rotation") + ": ");
        rotation = field(right + rotationLabel, 100, Math.max(24, rightWidth - rotationLabel), 11, rotationText);
        int third = Math.max(24, (rightWidth - 8) / 3);
        offsetX = field(right, 135, third, 16, xText);
        offsetY = field(right + third + 4, 135, third, 16, yText);
        offsetZ = field(right + (third + 4) * 2, 135, Math.max(24, rightWidth - (third + 4) * 2), 16, zText);
        buttonList.add(new GuiButton(0, width - 112, height - 28, 100, 20, I18n.format("gui.done")));
        buttonList.add(new GuiButton(1, width - 220, height - 28, 100, 20, I18n.format("gui.cancel")));
        filter();
    }
    private GuiTextField field(int x, int y, int w, int max, String value) {
        GuiTextField result = new GuiTextField(fontRendererObj, x, y, Math.max(20, w), 20);
        result.setMaxStringLength(max); result.setText(value); return result;
    }
    private int rows() { return Math.max(1, (bottom - top) / 24); }
    private void filter() {
        filtered.clear();
        String query = search.getText().toLowerCase(Locale.ROOT);
        String noModel = I18n.format("gui.sam.speaker.no_model");
        if (query.isEmpty() || noModel.toLowerCase(Locale.ROOT).contains(query)) filtered.add("");
        for (SpeakerModelDefinition model : SpeakerModelRegistry.list())
            if ((model.name + " " + model.displayName + " " + model.tags).toLowerCase(Locale.ROOT).contains(query))
                filtered.add(model.name);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - rows())));
    }
    private float offset(GuiTextField field) {
        float value = Float.parseFloat(field.getText().trim().replace(',', '.'));
        if (!TileEntitySpeaker.validOffset(value)) throw new NumberFormatException("Offset out of range");
        return value;
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 1) { mc.thePlayer.closeScreen(); return; }
        if (button.id != 0 || (!selected.isEmpty() && SpeakerModelRegistry.get(selected) == null)) return;
        try {
            int rangeValue = Integer.parseInt(range.getText().trim());
            float volumeValue = Float.parseFloat(volume.getText().trim().replace(',', '.'));
            int yaw = SwitchYaw.parse(rotation.getText().trim());
            PacketSpeakerConfig packet = new PacketSpeakerConfig(tile.xCoord, tile.yCoord, tile.zCoord,
                jp.me1han.sam.link.LinkKey.normalize(key.getText()), rangeValue, volumeValue, selected, yaw,
                offset(offsetX), offset(offsetY), offset(offsetZ));
            if (!packet.isValidPayload()) return;
            NetworkHandler.INSTANCE.sendToServer(packet); mc.thePlayer.closeScreen();
        } catch (RuntimeException ignored) { }
    }
    @Override public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("gui.sam.speaker.title"), width / 2, 6, 0xFFFFFF);
        drawString(fontRendererObj, I18n.format("gui.sam.speaker.search"), left, 19, 0xAAAAAA);
        drawString(fontRendererObj, I18n.format("gui.sam.link_key"), right, 19, 0xAAAAAA);
        search.drawTextBox(); key.drawTextBox();
        int half = Math.max(28, (rightWidth - 4) / 2);
        drawString(fontRendererObj, I18n.format("gui.sam.speaker.range"), right, 54, 0xAAAAAA);
        drawString(fontRendererObj, I18n.format("gui.sam.speaker.volume"), right + half + 4, 54, 0xAAAAAA);
        range.drawTextBox(); volume.drawTextBox();
        drawString(fontRendererObj, I18n.format("gui.sam.speaker.rotation") + ":", right, 89, 0xAAAAAA); rotation.drawTextBox();
        drawString(fontRendererObj, I18n.format("gui.sam.speaker.offset"), right, 124, 0xAAAAAA);
        offsetX.drawTextBox(); offsetY.drawTextBox(); offsetZ.drawTextBox();
        for (int row = 0; row < rows() && row + scroll < filtered.size(); row++) {
            String name = filtered.get(row + scroll); SpeakerModelDefinition model = SpeakerModelRegistry.get(name);
            String display = name.isEmpty() ? I18n.format("gui.sam.speaker.no_model") : model.displayName;
            int y = top + row * 24; boolean hover = mx >= left && mx < left + listWidth && my >= y && my < y + 24;
            drawRect(left, y, left + listWidth, y + 23, name.equals(selected) ? 0xFF365C78 : hover ? 0xFF444444 : 0xBB202020);
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(display, listWidth - 8), left + 4, y + 8, 0xFFFFFF);
        }
        if (filtered.isEmpty()) drawString(fontRendererObj, I18n.format("gui.sam.speaker.empty"), left + 4, top + 6, 0xAAAAAA);
        if (filtered.size() > rows()) {
            int bar = Math.max(8, (bottom - top) * rows() / filtered.size());
            int sy = top + (bottom - top - bar) * scroll / (filtered.size() - rows());
            drawRect(left + listWidth - 2, sy, left + listWidth, sy + bar, 0xFFAAAAAA);
        }
        SpeakerModelDefinition model = SpeakerModelRegistry.get(selected);
        ((GuiButton)buttonList.get(0)).enabled = selected.isEmpty() || model != null;
        if (!selected.isEmpty() && model == null)
            fontRendererObj.drawSplitString(I18n.format("gui.sam.speaker.missing") + ": " + selected, right, 160, rightWidth, 0xFF7777);
        else if (model != null) renderPreview(model);
        super.drawScreen(mx, my, partial);
    }
    private void renderPreview(SpeakerModelDefinition model) {
        MqoMesh mesh = SwitchMeshRenderer.INSTANCE.mesh(model);
        if (mesh == null) {
            fontRendererObj.drawSplitString(I18n.format("gui.sam.speaker.model_error"), right, 160, rightWidth, 0xFF7777); return;
        }
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (List<MqoMesh.Triangle> part : mesh.parts.values()) for (MqoMesh.Triangle face : part)
            for (double[] vertex : face.vertices) for (int i = 0; i < 3; i++) {
                double value = (vertex[i] + model.modelOffset[i]) * model.scale;
                min[i] = Math.min(min[i], value); max[i] = Math.max(max[i], value);
            }
        double extent = Math.max(.01, Math.max(max[0]-min[0], Math.max(max[1]-min[1], max[2]-min[2])));
        int previewHeight = Math.max(18, bottom - 162);
        double zoom = Math.min(rightWidth - 16, previewHeight) / (extent * 1.8);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT); GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glTranslated(right + rightWidth / 2.0, 160 + previewHeight / 2.0, 100);
            GL11.glScaled(zoom, -zoom, zoom); GL11.glRotatef(22, 1, 0, 0);
            float yaw = tile.getRotationYaw(); try { yaw = SwitchYaw.parse(rotation.getText()); } catch (RuntimeException ignored) { }
            GL11.glRotatef(35 + yaw, 0, 1, 0);
            GL11.glTranslated(-(min[0]+max[0])/2, -(min[1]+max[1])/2, -(min[2]+max[2])/2);
            RenderHelper.enableGUIStandardItemLighting();
            SwitchMeshRenderer.INSTANCE.render(model, false, 0xF000F0);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glPopMatrix(); GL11.glPopAttrib();
        }
    }
    @Override public void handleMouseInput() {
        super.handleMouseInput(); int delta = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / mc.displayWidth, y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (delta != 0 && x >= left && x < left + listWidth && y >= top && y < bottom) { scroll += delta > 0 ? -1 : 1; filter(); }
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        for (GuiTextField field : fields()) field.mouseClicked(x, y, button);
        int row = (y - top) / 24 + scroll;
        if (button == 0 && x >= left && x < left + listWidth && y >= top && y < top + rows()*24 && row >= 0 && row < filtered.size()) selected = filtered.get(row);
    }
    @Override protected void keyTyped(char c, int code) {
        for (GuiTextField field : fields()) if (field.isFocused()) {
            if (code == Keyboard.KEY_ESCAPE) super.keyTyped(c, code);
            else if (field.textboxKeyTyped(c, code) && field == search) { scroll = 0; filter(); }
            return;
        }
        super.keyTyped(c, code);
    }
    private GuiTextField[] fields() { return new GuiTextField[]{key, range, volume, search, rotation, offsetX, offsetY, offsetZ}; }
    @Override public void updateScreen() { for (GuiTextField field : fields()) field.updateCursorCounter(); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
