package jp.me1han.sam;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public class GuiAnnouncer extends GuiScreen {
    private TileEntityAnnouncer tile;
    private GuiTextField linkField; // リンクキー用

    public GuiAnnouncer(TileEntityAnnouncer tile) {
        this.tile = tile;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        // 既存のスクリプト選択ボタンなどは維持しつつ、下部にリンクキー入力欄を作成
        this.linkField = new GuiTextField(this.fontRendererObj, this.width / 2 - 100, 160, 200, 20);
        this.linkField.setText(tile.getLinkKey());

        this.buttonList.add(new GuiButton(100, this.width / 2 - 100, 190, 200, 20, "Save Settings"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 100) {
            // サーバーへリンクキー設定を送信するパケットを飛ばす
            // NetworkHandlerにMessageAnnouncerUpdateなどを追加する必要があります
            NetworkHandler.sendAnnouncerUpdate(tile.xCoord, tile.yCoord, tile.zCoord, tile.getScriptName(), linkField.getText());
            this.mc.displayGuiScreen(null);
        }
        // 他のボタン（スクリプト選択など）の処理...
    }

    @Override
    public void drawScreen(int x, int y, float f) {
        this.drawDefaultBackground();
        this.linkField.drawTextBox(); // 前回の修正通り drawTextBox を使用
        this.drawString(this.fontRendererObj, "Link Key:", this.width / 2 - 100, 148, 0xA0A0A0);
        super.drawScreen(x, y, f);
    }

    // keyTyped や mouseClicked で linkField.textboxKeyTyped(c, i) 等を呼ぶ必要があります
}
