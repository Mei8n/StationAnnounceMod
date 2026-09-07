package jp.me1han.sam.gui;

import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.api.AnnounceScriptInfo;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/** Shared script-name preview using metadata collected when packs are loaded. */
abstract class GuiScriptConfig extends GuiScreen {
    protected void drawScriptDisplayName(String fileName, int x, int y, int mouseX, int mouseY) {
        String key = fileName == null ? "" : fileName.trim();
        String name = I18n.format(key.isEmpty() ? "gui.sam.script.none" : "gui.sam.script.not_loaded");
        for (AnnounceScriptInfo info : AnnouncePackLoader.availableScripts) {
            if (info.fileName.equals(key)) {
                name = info.displayName == null || info.displayName.trim().isEmpty() ? info.fileName : info.displayName;
                break;
            }
        }
        String text = I18n.format("gui.sam.script.display_name", name);
        boolean shortened = fontRendererObj.getStringWidth(text) > 220;
        String visible = shortened
            ? fontRendererObj.trimStringToWidth(text, 220 - fontRendererObj.getStringWidth("...")) + "..." : text;
        drawString(fontRendererObj, visible, x, y, 0xD0D0D0);
        if (shortened && mouseX >= x && mouseX < x + 220 && mouseY >= y && mouseY < y + fontRendererObj.FONT_HEIGHT) {
            drawHoveringText(fontRendererObj.listFormattedStringToWidth(text, Math.max(80, Math.min(300, width - 40))),
                mouseX, mouseY, fontRendererObj);
        }
    }
}
