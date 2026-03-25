package jp.me1han.sam.gui;

import jp.me1han.sam.api.TrainTypeCondition;
import jp.me1han.sam.network.MessageTrainTypeConfig;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.render.TileEntityTrainTypeSelector;
import jp.me1han.sam.container.ContainerTrainTypeSelector;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import java.util.ArrayList;
import java.util.List;

public class GuiTrainTypeSelector extends GuiScreen {
    private List<ConditionRow> rows = new ArrayList<ConditionRow>();
    private static final String[] TYPE_NAMES = {"String", "Boolean", "Int", "Double"};
    private ContainerTrainTypeSelector container;
    private TileEntityTrainTypeSelector tile;

    // ★追加：リンクキー入力用のフィールドを宣言
    private GuiTextField linkKeyField;

    private boolean needsRefresh = false;

    public GuiTrainTypeSelector(ContainerTrainTypeSelector container, TileEntityTrainTypeSelector tile) {
        this.container = container;
        this.tile = tile;
        for (TrainTypeCondition cond : tile.conditions) {
            rows.add(new ConditionRow(cond.key, cond.type));
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        // ★追加：リンクキー入力欄の初期化（再描画時に中身が消えないようにする）
        if (this.linkKeyField == null) {
            this.linkKeyField = new GuiTextField(fontRendererObj, width / 2 - 60, 30, 180, 20);
            this.linkKeyField.setText(tile.linkKey != null ? tile.linkKey : "");
            this.linkKeyField.setMaxStringLength(32);
        } else {
            this.linkKeyField.xPosition = width / 2 - 60;
            this.linkKeyField.yPosition = 30;
        }

        // リンクキー欄の分、リストの開始位置を下にずらします
        int y = 70;
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setup(this.width / 2 - 120, y, i);
            y += 25;
        }

        // ボタンIDを重複しないように配置
        this.buttonList.add(new GuiButton(50, width / 2 - 100, height - 55, 90, 20, "Add Key"));
        this.buttonList.add(new GuiButton(51, width / 2 + 10, height - 55, 90, 20, "Done"));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (needsRefresh) {
            needsRefresh = false;
            this.initGui();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 50) { // Add Key
            rows.add(new ConditionRow("", 0));
            needsRefresh = true;
        } else if (button.id == 51) { // Done
            List<TrainTypeCondition> newConditions = new ArrayList<TrainTypeCondition>();
            for (ConditionRow row : this.rows) {
                if (row.keyField != null && !row.keyField.getText().isEmpty()) {
                    newConditions.add(new TrainTypeCondition(row.keyField.getText(), row.type));
                }
            }

            String keyToSend = this.linkKeyField != null ? this.linkKeyField.getText() : "";

            NetworkHandler.INSTANCE.sendToServer(new MessageTrainTypeConfig(
                tile.xCoord, tile.yCoord, tile.zCoord, newConditions, keyToSend
            ));

            this.mc.thePlayer.closeScreen();

        } else if (button.id >= 100 && button.id < 200) { // Type Toggle
            ConditionRow row = rows.get(button.id - 100);
            row.type = (row.type + 1) % TYPE_NAMES.length;
            button.displayString = TYPE_NAMES[row.type];
        } else if (button.id >= 200) { // Remove
            rows.remove(button.id - 200);
            needsRefresh = true;
        }
    }

    @Override
    public void drawScreen(int x, int y, float f) {
        this.drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Train Data Extractor Config", width / 2, 10, 0xFFFFFF);

        // ★追加：リンクキーのラベルとフィールドの描画
        drawString(fontRendererObj, "Link Key", width / 2 - 120, 35, 0xA0A0A0);
        if (this.linkKeyField != null) {
            this.linkKeyField.drawTextBox();
        }

        if (!rows.isEmpty()) {
            drawString(fontRendererObj, "Key Name", width / 2 - 120, 55, 0xA0A0A0);
            drawString(fontRendererObj, "Type", width / 2 + 50, 55, 0xA0A0A0);
        }

        for (ConditionRow row : rows) {
            if (row.keyField != null) row.keyField.drawTextBox();
        }
        super.drawScreen(x, y, f);
    }

    @Override
    protected void keyTyped(char c, int i) {
        if (i == 1) {
            this.mc.thePlayer.closeScreen();
            return;
        }

        // リンクキーの入力判定
        if (this.linkKeyField != null && this.linkKeyField.textboxKeyTyped(c, i)) {
            return;
        }

        for (ConditionRow row : rows) {
            if (row.keyField != null && row.keyField.isFocused()) {
                row.keyField.textboxKeyTyped(c, i);
                return;
            }
        }
        super.keyTyped(c, i);
    }

    @Override
    protected void mouseClicked(int x, int y, int b) {
        super.mouseClicked(x, y, b);
        // ★追加：リンクキーのクリック判定
        if (this.linkKeyField != null) {
            this.linkKeyField.mouseClicked(x, y, b);
        }

        for (ConditionRow row : rows) {
            if (row.keyField != null) row.keyField.mouseClicked(x, y, b);
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private class ConditionRow {
        public GuiTextField keyField;
        public int type;
        private String tempKey;

        public ConditionRow(String k, int t) {
            this.tempKey = k;
            this.type = t;
        }

        public void setup(int x, int y, int index) {
            if (this.keyField == null) {
                this.keyField = new GuiTextField(fontRendererObj, x, y, 160, 20);
                this.keyField.setText(tempKey);
            }
            this.keyField.yPosition = y;

            buttonList.add(new GuiButton(100 + index, x + 170, y, 60, 20, TYPE_NAMES[type]));
            buttonList.add(new GuiButton(200 + index, x + 235, y, 20, 20, "x"));
        }
    }
}
