package jp.me1han.sam.gui;

import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.api.AnnounceScriptInfo;
import jp.me1han.sam.container.ContainerAnnouncer;
import jp.me1han.sam.network.PacketConfig;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class GuiAnnouncer extends GuiScreen {
    private final ContainerAnnouncer container;
    private final TileEntityAnnouncer tile;
    private GuiTextField linkKeyField;
    private int selectedIndex = -1;

    // 自作リストの設定
    private final int slotHeight = 24;
    private int listTop;
    private int listBottom;
    private int listWidth = 220;
    private int listX;

    // スクロール状態
    private float scrollAmount = 0.0F;
    private boolean isScrollingBar = false;
    private int contentHeight;
    private int maxScroll;

    // ダブルクリック判定用
    private long lastClickTime = 0;
    private int lastClickedIndex = -1;

    public GuiAnnouncer(ContainerAnnouncer container, TileEntityAnnouncer tile) {
        this.container = container;
        this.tile = tile;

        // 現在のスクリプトを選択状態にする
        String current = tile.getScriptName();
        List<AnnounceScriptInfo> scripts = AnnouncePackLoader.availableScripts;
        for (int i = 0; i < scripts.size(); i++) {
            if (scripts.get(i).fileName.equals(current)) {
                this.selectedIndex = i;
                break;
            }
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        // リンクキー入力欄
        this.linkKeyField = new GuiTextField(fontRendererObj, width / 2 - 100, 35, 200, 20);
        this.linkKeyField.setText(tile.linkKey != null ? tile.linkKey : "");

        // 完了ボタン
        this.buttonList.add(new GuiButton(0, width / 2 - 100, height - 30, "Done"));

        // リスト描画領域の計算
        this.listX = width / 2 - listWidth / 2;
        this.listTop = 60; // ラベルの下
        this.listBottom = height - 40; // ボタンの上

        this.contentHeight = AnnouncePackLoader.availableScripts.size() * slotHeight;
        this.maxScroll = Math.max(0, this.contentHeight - (listBottom - listTop));

        // selectedIndexの位置まで初期スクロール
        if (selectedIndex >= 0) {
            this.scrollAmount = selectedIndex * slotHeight;
            this.scrollAmount = MathHelper.clamp_float(this.scrollAmount, 0.0F, (float)maxScroll);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            saveAndClose();
        }
    }

    private void saveAndClose() {
        String selectedFile = (selectedIndex >= 0 && selectedIndex < AnnouncePackLoader.availableScripts.size())
            ? AnnouncePackLoader.availableScripts.get(selectedIndex).fileName
            : "";

        NetworkHandler.INSTANCE.sendToServer(new PacketConfig(tile.xCoord, tile.yCoord, tile.zCoord, selectedFile, linkKeyField.getText()));
        this.mc.thePlayer.closeScreen();
    }

    @Override
    protected void keyTyped(char c, int i) {
        if (i == 1) { // ESC
            this.mc.thePlayer.closeScreen();
            return;
        }
        if (this.linkKeyField.textboxKeyTyped(c, i)) return;
        super.keyTyped(c, i);
    }

    @Override
    protected void mouseClicked(int x, int y, int b) {
        super.mouseClicked(x, y, b);
        this.linkKeyField.mouseClicked(x, y, b);

        int scrollBarX = listX + listWidth + 2;
        int scrollBarWidth = 6;

        // --- スクロールバーのクリック判定 ---
        if (x >= scrollBarX && x < scrollBarX + scrollBarWidth && y >= listTop && y < listBottom && maxScroll > 0) {
            this.isScrollingBar = true;
            this.updateScrollFromMouse(y);
            return;
        }

        // --- リスト内クリック判定 ---
        if (x >= listX && x < listX + listWidth && y >= listTop && y < listBottom) {
            float relY = y - listTop + scrollAmount;
            int clickedIndex = (int) (relY / slotHeight);

            if (clickedIndex >= 0 && clickedIndex < AnnouncePackLoader.availableScripts.size()) {
                this.selectedIndex = clickedIndex;

                // ダブルクリック判定
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < 500 && clickedIndex == lastClickedIndex) {
                    saveAndClose(); // ダブルクリックで決定して閉じる
                }
                lastClickTime = currentTime;
                lastClickedIndex = clickedIndex;
            }
        }
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int state) {
        super.mouseMovedOrUp(x, y, state);
        if (state == 0) { // Mouse Up
            this.isScrollingBar = false;
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int lastButton, long timeSinceClick) {
        super.mouseClickMove(x, y, lastButton, timeSinceClick);
        if (this.isScrollingBar) {
            this.updateScrollFromMouse(y);
        }
    }

    // マウスY座標からスクロール位置を計算
    private void updateScrollFromMouse(int mouseY) {
        int listHeight = listBottom - listTop;
        float ratio = (float)(mouseY - listTop) / (float)listHeight;
        this.scrollAmount = ratio * maxScroll;
        this.scrollAmount = MathHelper.clamp_float(this.scrollAmount, 0.0F, (float)maxScroll);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        // マウスホイール
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && maxScroll > 0) {
            // スクロール方向を反転 (下ホイールでリストを下げる＝scrollAmountを増やす)
            int direction = wheel > 0 ? -1 : 1;

            // ホイールスクロール量 (半スロット分)
            this.scrollAmount += (direction * (slotHeight / 2.0F));
            this.scrollAmount = MathHelper.clamp_float(this.scrollAmount, 0.0F, (float)maxScroll);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float f) {
        // SAM共通：半透明背景 (drawGradientRect)
        this.drawGradientRect(0, 0, this.width, this.height, -1072689136, -804253680);

        // ラベル描画
        drawCenteredString(fontRendererObj, "Announcer Config", width / 2, 8, 0xFFFFFF);
        drawString(fontRendererObj, "Link Key", width / 2 - 100, 25, 0xA0A0A0);
        this.linkKeyField.drawTextBox();

        // --- 自作リストの描画 ---
        int listHeight = listBottom - listTop;

        // リスト背景矩形 (少し薄く)
        drawRect(listX, listTop, listX + listWidth, listBottom, 0x60000000);

        // クリッピング設定 (SCISSOR TEST)
        // Scissor原点は左下なので、ScaledResolutionを使ってY座標を反転させる必要がある
        net.minecraft.client.gui.ScaledResolution res = new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = res.getScaleFactor();

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        // glScissor(x, y(左下原点), width, height)
        GL11.glScissor(listX * scale, (height - listBottom) * scale, listWidth * scale, listHeight * scale);

        // アイテム描画ループ
        List<AnnounceScriptInfo> scripts = AnnouncePackLoader.availableScripts;
        for (int i = 0; i < scripts.size(); i++) {
            // 現在のスクロール量を考慮したY座標
            int slotY = listTop + (i * slotHeight) - (int)scrollAmount;

            // 完全に描画範囲外なら飛ばす/ループ終了
            if (slotY + slotHeight < listTop) continue;
            if (slotY > listBottom) break;

            AnnounceScriptInfo info = scripts.get(i);

            // 選択中 (selectedIndex) のハイライト
            if (i == selectedIndex) {
                drawRect(listX, slotY, listX + listWidth, slotY + slotHeight, 0x40FFFFFF);
            }

            // マウスオーバーのハイライト (リスト領域内かつアイテム上にある場合)
            if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= slotY && mouseY < slotY + slotHeight && mouseY >= listTop && mouseY < listBottom) {
                drawRect(listX, slotY, listX + listWidth, slotY + slotHeight, 0x20FFFFFF);
            }

            // テキスト描画
            // 表示名 (getDisplayName)
            drawString(fontRendererObj, info.displayName, listX + 5, slotY + 2, 0xFFFFFF);
            // ファイル名 (グレー)
            drawString(fontRendererObj, "(" + info.fileName + ")", listX + 5, slotY + 12, 0x808080);
        }

        // クリッピング解除
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // --- スクロールバーの描画 ---
        if (maxScroll > 0) {
            int scrollBarX = listX + listWidth + 2;
            int scrollBarWidth = 6;

            // トラック (背景)
            drawRect(scrollBarX, listTop, scrollBarX + scrollBarWidth, listBottom, 0x40000000);

            // ノブ (つまみ) の計算
            int knobHeight = (int) ((float)listHeight / (float)contentHeight * listHeight);
            knobHeight = MathHelper.clamp_int(knobHeight, 10, listHeight); // 最低10px

            int knobY = listTop + (int) (scrollAmount / maxScroll * (listHeight - knobHeight));

            // ノブ描画 (白)
            drawRect(scrollBarX, knobY, scrollBarX + scrollBarWidth, knobY + knobHeight, 0xFFFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, f);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }
}
