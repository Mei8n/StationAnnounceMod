package jp.me1han.sam.gui;

import java.util.*;
import jp.me1han.sam.item.ItemDepartureSwitch;
import jp.me1han.sam.network.*;
import jp.me1han.sam.switchmodel.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** RTM-style model picker for an unplaced departure-switch item. */
public class GuiDepartureSwitchItem extends GuiScreen {
    private final int slot;
    private String selected;
    private GuiTextField search;
    private int scroll, left, listWidth, top, bottom;
    private final List<SwitchModelDefinition> filtered = new ArrayList<>();

    public GuiDepartureSwitchItem(ItemStack stack, int slot) {
        this.slot = slot;
        this.selected = ItemDepartureSwitch.selectedModel(stack);
    }

    @Override public void initGui() {
        String searchText = search == null ? "" : search.getText();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        listWidth = Math.min(320, width - 24);
        left = (width - listWidth) / 2;
        top = 62;
        bottom = height - 38;
        search = new GuiTextField(fontRendererObj, left, 34, listWidth, 20);
        search.setMaxStringLength(128);
        search.setText(searchText);
        buttonList.add(new GuiButton(0, width / 2 + 4, height - 28, 100, 20, I18n.format("gui.done")));
        buttonList.add(new GuiButton(1, width / 2 - 104, height - 28, 100, 20, I18n.format("gui.cancel")));
        filter();
    }

    private int rows() { return Math.max(1, (bottom - top) / 24); }

    private void filter() {
        filtered.clear();
        String query = search.getText().toLowerCase(Locale.ROOT);
        for (SwitchModelDefinition model : SwitchModelRegistry.list()) {
            if ((model.name + " " + model.displayName + " " + model.tags).toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(model);
            }
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - rows())));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0 && SwitchModelRegistry.get(selected) != null) {
            NetworkHandler.INSTANCE.sendToServer(new PacketDepartureSwitchItemConfig(slot, selected));
            mc.thePlayer.closeScreen();
        } else if (button.id == 1) {
            mc.thePlayer.closeScreen();
        }
    }

    @Override public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("gui.sam.switch.item_title"), width / 2, 8, 0xFFFFFF);
        drawString(fontRendererObj, I18n.format("gui.sam.switch.search"), left, 23, 0xAAAAAA);
        search.drawTextBox();
        for (int row = 0; row < rows() && row + scroll < filtered.size(); row++) {
            SwitchModelDefinition model = filtered.get(row + scroll);
            int y = top + row * 24;
            boolean hover = mx >= left && mx < left + listWidth && my >= y && my < y + 24;
            drawRect(left, y, left + listWidth, y + 23,
                model.name.equals(selected) ? 0xFF365C78 : hover ? 0xFF444444 : 0xBB202020);
            int reserve = model.buttonTexture.isEmpty() ? 8 : 32;
            String text = fontRendererObj.trimStringToWidth(model.displayName, listWidth - reserve);
            drawString(fontRendererObj, text, left + 4, y + 8, 0xFFFFFF);
            if (!model.buttonTexture.isEmpty()) {
                mc.getTextureManager().bindTexture(new ResourceLocation(model.buttonTexture));
                GL11.glColor4f(1, 1, 1, 1);
                texture(left + 8 + fontRendererObj.getStringWidth(text), y + 3, 18, 18);
            }
        }
        if (filtered.isEmpty()) {
            drawString(fontRendererObj, I18n.format("gui.sam.switch.empty"), left + 4, top + 6, 0xAAAAAA);
        }
        if (filtered.size() > rows()) {
            int bar = Math.max(8, (bottom - top) * rows() / filtered.size());
            int y = top + (bottom - top - bar) * scroll / (filtered.size() - rows());
            drawRect(left + listWidth - 2, y, left + listWidth, y + bar, 0xFFAAAAAA);
        }
        ((GuiButton) buttonList.get(0)).enabled = SwitchModelRegistry.get(selected) != null;
        super.drawScreen(mx, my, partial);
    }

    private void texture(int x, int y, int w, int h) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + h, 0, 0, 1);
        tess.addVertexWithUV(x + w, y + h, 0, 1, 1);
        tess.addVertexWithUV(x + w, y, 0, 1, 0);
        tess.addVertexWithUV(x, y, 0, 0, 0);
        tess.draw();
    }

    @Override public void handleMouseInput() {
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (delta != 0 && x >= left && x < left + listWidth && y >= top && y < bottom) {
            scroll += delta > 0 ? -1 : 1;
            filter();
        }
    }

    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        search.mouseClicked(x, y, button);
        int row = (y - top) / 24 + scroll;
        if (button == 0 && x >= left && x < left + listWidth && y >= top
            && y < top + rows() * 24 && row >= 0 && row < filtered.size()) {
            selected = filtered.get(row).name;
        }
    }

    @Override protected void keyTyped(char c, int code) {
        if (search.textboxKeyTyped(c, code)) {
            scroll = 0;
            filter();
        } else {
            super.keyTyped(c, code);
        }
    }

    @Override public void updateScreen() { search.updateCursorCounter(); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
