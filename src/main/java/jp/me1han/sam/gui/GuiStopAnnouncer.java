package jp.me1han.sam.gui;

import jp.me1han.sam.network.PacketStopAnnouncerConfig;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.render.TileEntityStopAnnouncer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public class GuiStopAnnouncer extends GuiScreen {
    private final TileEntityStopAnnouncer tile;
    private GuiTextField linkKeyField;

    public GuiStopAnnouncer(TileEntityStopAnnouncer tile) {
        this.tile = tile;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        // 画面中央に入力欄を配置
        this.linkKeyField = new GuiTextField(fontRendererObj, width / 2 - 100, height / 2 - 20, 200, 20);
        this.linkKeyField.setText(tile.linkKey != null ? tile.linkKey : "");
        this.linkKeyField.setMaxStringLength(32);
        this.linkKeyField.setFocused(true);

        // 完了ボタン
        this.buttonList.add(new GuiButton(0, width / 2 - 100, height / 2 + 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            // サーバーへ停止装置の設定（リンクキー）を送信
            NetworkHandler.INSTANCE.sendToServer(new PacketStopAnnouncerConfig(
                tile.xCoord, tile.yCoord, tile.zCoord, linkKeyField.getText()
            ));
            this.mc.thePlayer.closeScreen();
        }
    }

    @Override
    protected void keyTyped(char c, int i) {
        if (i == 1) { // ESC
            this.mc.thePlayer.closeScreen();
            return;
        }

        // テキストボックスの入力を優先
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

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }
}
