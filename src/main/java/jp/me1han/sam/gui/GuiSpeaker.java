package jp.me1han.sam.gui;

import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.network.PacketSpeakerConfig;
import jp.me1han.sam.render.TileEntitySpeaker;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public class GuiSpeaker extends GuiScreen {
    private final TileEntitySpeaker tile;
    private GuiTextField linkKeyField;
    private GuiTextField rangeField;
    private GuiTextField volumeField;

    public GuiSpeaker(TileEntitySpeaker tile) { this.tile = tile; }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = width / 2;
        int centerY = height / 2;

        // リンクキー
        linkKeyField = new GuiTextField(fontRendererObj, centerX - 100, centerY - 60, 200, 20);
        linkKeyField.setText(tile.linkKey);

        // 範囲 (数値のみ想定)
        rangeField = new GuiTextField(fontRendererObj, centerX - 100, centerY - 20, 90, 20);
        rangeField.setText(String.valueOf(tile.range));

        // 音量 (0.0 - 1.0)
        volumeField = new GuiTextField(fontRendererObj, centerX + 10, centerY - 20, 90, 20);
        volumeField.setText(String.valueOf(tile.volume));

        this.buttonList.add(new GuiButton(0, centerX - 100, centerY + 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            try {
                int range = Integer.parseInt(rangeField.getText());
                float vol = Float.parseFloat(volumeField.getText());
                NetworkHandler.INSTANCE.sendToServer(new PacketSpeakerConfig(tile.xCoord, tile.yCoord, tile.zCoord, linkKeyField.getText(), range, vol));
                this.mc.thePlayer.closeScreen();
            } catch (Exception e) {
                // 数値変換エラー時は保存せず閉じるか、赤文字などで警告を出す
            }
        }
    }

    @Override
    public void drawScreen(int x, int y, float f) {
        this.drawGradientRect(0, 0, this.width, this.height, -1072689136, -804253680);
        drawCenteredString(fontRendererObj, "Speaker Config", width / 2, height / 2 - 85, 0xFFFFFF);

        drawString(fontRendererObj, "Link Key", width / 2 - 100, height / 2 - 75, 0xA0A0A0);
        drawString(fontRendererObj, "Range (blocks)", width / 2 - 100, height / 2 - 35, 0xA0A0A0);
        drawString(fontRendererObj, "Volume (0.0 - 1.0)", width / 2 + 10, height / 2 - 35, 0xA0A0A0);

        linkKeyField.drawTextBox();
        rangeField.drawTextBox();
        volumeField.drawTextBox();
        super.drawScreen(x, y, f);
    }

    @Override
    protected void keyTyped(char c, int i) {
        if (i == 1) this.mc.thePlayer.closeScreen();
        linkKeyField.textboxKeyTyped(c, i);
        rangeField.textboxKeyTyped(c, i);
        volumeField.textboxKeyTyped(c, i);
    }

    @Override
    protected void mouseClicked(int x, int y, int b) {
        super.mouseClicked(x, y, b);
        linkKeyField.mouseClicked(x, y, b);
        rangeField.mouseClicked(x, y, b);
        volumeField.mouseClicked(x, y, b);
    }

    @Override
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
