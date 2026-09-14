package jp.me1han.sam.gui;

import java.util.*;
import jp.me1han.sam.item.ItemSpeaker;
import jp.me1han.sam.network.*;
import jp.me1han.sam.speakermodel.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Model picker for an unplaced Speaker item, including the explicit No Model choice. */
public class GuiSpeakerItem extends GuiScreen {
    private final int slot;
    private String selected;
    private GuiTextField search;
    private int scroll, left, listWidth, top, bottom;
    private final List<String> filtered = new ArrayList<>();

    public GuiSpeakerItem(ItemStack stack, int slot) {
        this.slot = slot;
        this.selected = ItemSpeaker.selectedModel(stack);
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
        search.setMaxStringLength(PacketLimits.MODEL);
        search.setText(searchText);
        buttonList.add(new GuiButton(0, width / 2 + 4, height - 28, 100, 20, I18n.format("gui.done")));
        buttonList.add(new GuiButton(1, width / 2 - 104, height - 28, 100, 20, I18n.format("gui.cancel")));
        filter();
    }

    private int rows() { return Math.max(1, (bottom - top) / 24); }

    private void filter() {
        filtered.clear();
        String query = search.getText().toLowerCase(Locale.ROOT);
        String noModel = I18n.format("gui.sam.speaker.no_model");
        if (query.isEmpty() || noModel.toLowerCase(Locale.ROOT).contains(query)) filtered.add("");
        for (SpeakerModelDefinition model : SpeakerModelRegistry.list()) {
            if ((model.name + " " + model.displayName + " " + model.tags).toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(model.name);
            }
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - rows())));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0 && (selected.isEmpty() || SpeakerModelRegistry.get(selected) != null)) {
            PacketSpeakerItemConfig packet = new PacketSpeakerItemConfig(slot, selected);
            if (!packet.isValidPayload()) return;
            NetworkHandler.INSTANCE.sendToServer(packet);
            mc.thePlayer.closeScreen();
        } else if (button.id == 1) {
            mc.thePlayer.closeScreen();
        }
    }

    @Override public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("gui.sam.speaker.item_title"), width / 2, 8, 0xFFFFFF);
        drawString(fontRendererObj, I18n.format("gui.sam.speaker.search"), left, 23, 0xAAAAAA);
        search.drawTextBox();
        for (int row = 0; row < rows() && row + scroll < filtered.size(); row++) {
            String name = filtered.get(row + scroll);
            SpeakerModelDefinition model = SpeakerModelRegistry.get(name);
            String display = name.isEmpty() ? I18n.format("gui.sam.speaker.no_model") : model.displayName;
            int y = top + row * 24;
            boolean hover = mx >= left && mx < left + listWidth && my >= y && my < y + 24;
            drawRect(left, y, left + listWidth, y + 23,
                name.equals(selected) ? 0xFF365C78 : hover ? 0xFF444444 : 0xBB202020);
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(display, listWidth - 8),
                left + 4, y + 8, 0xFFFFFF);
        }
        if (filtered.isEmpty()) {
            drawString(fontRendererObj, I18n.format("gui.sam.speaker.empty"), left + 4, top + 6, 0xAAAAAA);
        }
        if (!selected.isEmpty() && SpeakerModelRegistry.get(selected) == null) {
            drawCenteredString(fontRendererObj, I18n.format("gui.sam.speaker.missing") + ": " + selected,
                width / 2, height - 38, 0xFF7777);
        }
        if (filtered.size() > rows()) {
            int bar = Math.max(8, (bottom - top) * rows() / filtered.size());
            int y = top + (bottom - top - bar) * scroll / (filtered.size() - rows());
            drawRect(left + listWidth - 2, y, left + listWidth, y + bar, 0xFFAAAAAA);
        }
        ((GuiButton)buttonList.get(0)).enabled = selected.isEmpty() || SpeakerModelRegistry.get(selected) != null;
        super.drawScreen(mx, my, partial);
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
            selected = filtered.get(row);
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
