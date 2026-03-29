package jp.me1han.sam.gui;

import jp.me1han.sam.network.PacketStopAnnouncerConfig;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.render.TileEntityStopAnnouncer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import cpw.mods.fml.client.config.GuiCheckBox; // ★追加
import org.lwjgl.input.Keyboard;

public class GuiStopAnnouncer extends GuiScreen {
    private final TileEntityStopAnnouncer tile;
    private GuiTextField linkKeyField;
    private GuiCheckBox chkControlCar;

    public GuiStopAnnouncer(TileEntityStopAnnouncer tile) {
        this.tile = tile;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        this.linkKeyField = new GuiTextField(fontRendererObj, width / 2 - 100, height / 2 - 20, 200, 20);
        this.linkKeyField.setText(tile.linkKey != null ? tile.linkKey : "");
        this.linkKeyField.setFocused(true);

        this.chkControlCar = new GuiCheckBox(1, width / 2 - 100, height / 2 + 5, "Control Car Only", tile.isControlCar);
        this.buttonList.add(chkControlCar);

        this.buttonList.add(new GuiButton(0, width / 2 - 100, height / 2 + 30, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            NetworkHandler.INSTANCE.sendToServer(new PacketStopAnnouncerConfig(tile.xCoord, tile.yCoord, tile.zCoord, linkKeyField.getText(), chkControlCar.isChecked()));
            this.tile.linkKey = this.linkKeyField.getText();
            this.tile.isControlCar = this.chkControlCar.isChecked();
            this.mc.thePlayer.closeScreen();
        }
    }

    @Override
    protected void keyTyped(char c, int i) {
        if (i == 1) { this.mc.thePlayer.closeScreen(); return; }
        if (this.linkKeyField.textboxKeyTyped(c, i)) return;
        super.keyTyped(c, i);
    }

    @Override
    protected void mouseClicked(int x, int y, int b) {
        super.mouseClicked(x, y, b);
        this.linkKeyField.mouseClicked(x, y, b);
    }

    @Override
    public void drawScreen(int x, int y, float f) {
        this.drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Stop Announcer Config", width / 2, height / 2 - 50, 0xFFFFFF);
        drawString(fontRendererObj, "Link Key", width / 2 - 100, height / 2 - 35, 0xA0A0A0);
        this.linkKeyField.drawTextBox();
        super.drawScreen(x, y, f);
    }
}
