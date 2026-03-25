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

    public GuiTrainTypeSelector(ContainerTrainTypeSelector container, TileEntityTrainTypeSelector tile) {
        this.container = container;
        this.tile = tile;

        for (TrainTypeCondition cond : tile.conditions) {
            rows.add(new ConditionRow(cond.key, cond.type));
        }
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int y = 70;
        for (int i = 0; i < rows.size(); i++) {
            ConditionRow row = rows.get(i);
            row.init(y, i);
            y += 25;
        }
        this.buttonList.add(new GuiButton(50, width / 2 - 100, height - 60, 90, 20, "Add Key"));
        this.buttonList.add(new GuiButton(51, width / 2 + 10, height - 60, 90, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 50) { // Add Key
            rows.add(new ConditionRow("", 0));
            // initGui() を直接呼ぶのではなく、解像度再設定を介して安全にリロードする
            this.setWorldAndResolution(this.mc, this.width, this.height);

        } else if (button.id == 51) { // Done
            // ... 保存処理 ...
            this.mc.displayGuiScreen(null);

        } else if (button.id >= 100 && button.id < 200) { // Type Toggle
            ConditionRow row = rows.get(button.id - 100);
            row.type = (row.type + 1) % TYPE_NAMES.length;
            button.displayString = TYPE_NAMES[row.type];

        } else if (button.id >= 200) { // Remove
            rows.remove(button.id - 200);
            // 安全にリロード
            this.setWorldAndResolution(this.mc, this.width, this.height);
        }
    }

    @Override
    public void drawScreen(int x, int y, float f) {
        this.drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Train Data Extractor Config", width / 2, 20, 0xFFFFFF);
        drawString(fontRendererObj, "Extract Key Name", width / 2 - 120, 52, 0xA0A0A0);
        drawString(fontRendererObj, "Data Type", width / 2 + 50, 52, 0xA0A0A0);

        for (ConditionRow row : rows) {
            row.keyField.drawTextBox();
        }
        super.drawScreen(x, y, f);
    }

    private class ConditionRow {
        public GuiTextField keyField;
        public int type;
        public GuiButton typeButton;
        public GuiButton removeButton;

        public ConditionRow(String k, int t) {
            // コンストラクタでは初期化のみ
            this.keyField = new GuiTextField(fontRendererObj, 0, 0, 160, 20);
            this.keyField.setText(k);
            this.type = t;
        }

        public void init(int y, int i) {
            // initGui() 時に座標を確定させる
            keyField.xPosition = width / 2 - 120;
            keyField.yPosition = y;
            typeButton = new GuiButton(100 + i, width / 2 + 50, y, 60, 20, TYPE_NAMES[type]);
            removeButton = new GuiButton(200 + i, width / 2 + 115, y, 20, 20, "x");
            buttonList.add(typeButton);
            buttonList.add(removeButton);
        }
    }

    // keyTyped, mouseClicked は keyField のみ処理するように簡略化
    @Override
    protected void keyTyped(char c, int i) {
        super.keyTyped(c, i);
        for (ConditionRow row : rows) row.keyField.textboxKeyTyped(c, i);
    }

    @Override
    protected void mouseClicked(int x, int y, int b) {
        super.mouseClicked(x, y, b);
        for (ConditionRow row : rows) row.keyField.mouseClicked(x, y, b);
    }
}
