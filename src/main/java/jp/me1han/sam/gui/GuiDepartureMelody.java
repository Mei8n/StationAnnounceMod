package jp.me1han.sam.gui;

import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.network.PacketDepartureMelodyConfig;
import jp.me1han.sam.render.TileEntityDepartureMelody;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public class GuiDepartureMelody extends GuiScreen {
    private final TileEntityDepartureMelody tile;
    private GuiTextField linkKeyField;
    private GuiTextField soundIdField;

    public GuiDepartureMelody(TileEntityDepartureMelody tile) {
        this.tile = tile;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        int left = this.width / 2 - 110;
        int top = this.height / 2 - 70;

        this.linkKeyField = new GuiTextField(this.fontRendererObj, left, top + 20, 220, 20);
        this.linkKeyField.setMaxStringLength(64);
        this.linkKeyField.setText(this.tile.linkKey == null ? "" : this.tile.linkKey);
        this.soundIdField = new GuiTextField(this.fontRendererObj, left, top + 60, 220, 20);
        this.soundIdField.setMaxStringLength(256);
        this.soundIdField.setText(this.tile.soundId == null ? "" : this.tile.soundId);
        this.buttonList.add(new GuiButton(0, left, top + 95, 220, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            String linkKey = this.linkKeyField.getText() == null ? "" : this.linkKeyField.getText().trim();
            String soundId = this.soundIdField.getText() == null ? "" : this.soundIdField.getText().trim();
            NetworkHandler.INSTANCE.sendToServer(new PacketDepartureMelodyConfig(
                this.tile.xCoord, this.tile.yCoord, this.tile.zCoord, linkKey, soundId));
            this.tile.applyConfig(linkKey, soundId);
            this.mc.thePlayer.closeScreen();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int left = this.width / 2 - 110;
        int top = this.height / 2 - 70;
        drawCenteredString(this.fontRendererObj, "Departure Melody Config", this.width / 2, top, 0xFFFFFF);
        drawString(this.fontRendererObj, "Link Key", left, top + 10, 0xA0A0A0);
        drawString(this.fontRendererObj, "Sound ID (one complete file)", left, top + 50, 0xA0A0A0);
        this.linkKeyField.drawTextBox();
        this.soundIdField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char c, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.thePlayer.closeScreen();
            return;
        }
        if (this.linkKeyField.textboxKeyTyped(c, keyCode)) return;
        if (this.soundIdField.textboxKeyTyped(c, keyCode)) return;
        super.keyTyped(c, keyCode);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        this.linkKeyField.mouseClicked(x, y, button);
        this.soundIdField.mouseClicked(x, y, button);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }
}
