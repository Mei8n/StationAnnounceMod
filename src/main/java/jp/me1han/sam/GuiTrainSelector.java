package jp.me1han.sam;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public class GuiTrainSelector extends GuiScreen {
    private TileEntityTrainSelector tile;
    private GuiTextField linkField;
    private GuiTextField dataKeyField;
    private static final String[] TYPES = {"String", "Int", "Boolean", "Double"};

    public GuiTrainSelector(TileEntityTrainSelector tile) {
        this.tile = tile;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        // リンクキー入力
        this.linkField = new GuiTextField(this.fontRendererObj, this.width / 2 - 100, 60, 200, 20);
        this.linkField.setText(tile.getLinkKey());

        // DataMapキー入力
        this.dataKeyField = new GuiTextField(this.fontRendererObj, this.width / 2 - 100, 100, 200, 20);
        this.dataKeyField.setText(tile.getDataKey());

        // 型切り替えボタン
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, 140, 200, 20, "Type: " + TYPES[tile.getDataType()]));
        // 保存ボタン
        this.buttonList.add(new GuiButton(1, this.width / 2 - 100, 180, 200, 20, "Save"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            int nextType = (tile.getDataType() + 1) % TYPES.length;
            tile.setDataType(nextType);
            button.displayString = "Type: " + TYPES[nextType];
        } else if (button.id == 1) {
            // サーバーへ設定を送信 (パケットの実装が必要)
            NetworkHandler.sendSelectorUpdate(tile.xCoord, tile.yCoord, tile.zCoord, linkField.getText(), dataKeyField.getText(), tile.getDataType());
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int x, int y, float f) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, "Train Selector Settings", this.width / 2, 20, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "Link Key (e.g. sta_up)", this.width / 2 - 100, 48, 0xA0A0A0);
        this.drawString(this.fontRendererObj, "DataMap Key", this.width / 2 - 100, 88, 0xA0A0A0);
        this.linkField.drawTextBox();
        this.dataKeyField.drawTextBox();
        super.drawScreen(x, y, f);
    }

    // keyTyped, mouseClicked 等の基本処理は省略
}
