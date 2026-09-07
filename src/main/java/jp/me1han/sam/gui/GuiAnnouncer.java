package jp.me1han.sam.gui;

import jp.me1han.sam.container.ContainerAnnouncer;
import jp.me1han.sam.network.PacketConfig;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import cpw.mods.fml.client.config.GuiCheckBox;
import org.lwjgl.input.Keyboard;

public class GuiAnnouncer extends GuiScriptConfig {
    private final TileEntityAnnouncer tile;
    private GuiTextField linkKeyField;
    private GuiTextField scriptNameField;
    private GuiCheckBox chkPlayLocal;

    public GuiAnnouncer(ContainerAnnouncer container, TileEntityAnnouncer tile) {
        this.tile = tile;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        int left = (this.width - 240) / 2;
        int top = (this.height - 155) / 2;
        this.linkKeyField = new GuiTextField(fontRendererObj, left + 10, top + 20, 220, 16);
        this.linkKeyField.setMaxStringLength(32);
        this.linkKeyField.setText(tile.linkKey == null ? "" : tile.linkKey);
        this.chkPlayLocal = new GuiCheckBox(1, left + 10, top + 45,
            I18n.format("gui.sam.announcer.play_local"), tile.playLocalSound);
        this.buttonList.add(chkPlayLocal);
        this.scriptNameField = new GuiTextField(fontRendererObj, left + 10, top + 80, 220, 16);
        this.scriptNameField.setMaxStringLength(256);
        this.scriptNameField.setText(tile.getScriptName() == null ? "" : tile.getScriptName());
        this.buttonList.add(new GuiButton(0, left + 10, top + 125, 220, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id != 0) return;
        String scriptName = scriptNameField.getText().trim();
        NetworkHandler.INSTANCE.sendToServer(new PacketConfig(
            tile.xCoord, tile.yCoord, tile.zCoord, scriptName,
            linkKeyField.getText(), chkPlayLocal.isChecked()));
        this.tile.setScriptName(scriptName);
        this.tile.linkKey = this.linkKeyField.getText();
        this.tile.playLocalSound = this.chkPlayLocal.isChecked();
        this.mc.thePlayer.closeScreen();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int left = (this.width - 240) / 2;
        int top = (this.height - 155) / 2;
        drawString(fontRendererObj, I18n.format("gui.sam.link_key"), left + 10, top + 8, 0xA0A0A0);
        drawString(fontRendererObj, I18n.format("gui.sam.announcer.script"), left + 10, top + 68, 0xA0A0A0);
        this.linkKeyField.drawTextBox();
        this.scriptNameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawScriptDisplayName(this.scriptNameField.getText(), left + 10, top + 103, mouseX, mouseY);
    }

    @Override
    protected void keyTyped(char c, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.thePlayer.closeScreen();
            return;
        }
        if (this.linkKeyField.textboxKeyTyped(c, keyCode)) return;
        if (this.scriptNameField.textboxKeyTyped(c, keyCode)) return;
        super.keyTyped(c, keyCode);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        this.linkKeyField.mouseClicked(x, y, button);
        this.scriptNameField.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        this.linkKeyField.updateCursorCounter();
        this.scriptNameField.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
