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

    private final int slotHeight = 24;
    private int listTop;
    private int listBottom;
    private int listWidth = 220;
    private int listX;

    private float scrollAmount = 0.0F;
    private boolean isScrollingBar = false;
    private int contentHeight;
    private int maxScroll;

    private long lastClickTime = 0;
    private int lastClickedIndex = -1;

    public GuiAnnouncer(ContainerAnnouncer container, TileEntityAnnouncer tile) {
        this.container = container;
        this.tile = tile;

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

        this.linkKeyField = new GuiTextField(fontRendererObj, width / 2 - 100, 35, 200, 20);
        this.linkKeyField.setText(tile.linkKey != null ? tile.linkKey : "");

        this.buttonList.add(new GuiButton(0, width / 2 - 100, height - 30, "Done"));

        this.listX = width / 2 - listWidth / 2;
        this.listTop = 60;
        this.listBottom = height - 40;

        this.contentHeight = AnnouncePackLoader.availableScripts.size() * slotHeight;
        this.maxScroll = Math.max(0, this.contentHeight - (listBottom - listTop));

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

        if (x >= scrollBarX && x < scrollBarX + scrollBarWidth && y >= listTop && y < listBottom && maxScroll > 0) {
            this.isScrollingBar = true;
            this.updateScrollFromMouse(y);
            return;
        }

        if (x >= listX && x < listX + listWidth && y >= listTop && y < listBottom) {
            float relY = y - listTop + scrollAmount;
            int clickedIndex = (int) (relY / slotHeight);

            if (clickedIndex >= 0 && clickedIndex < AnnouncePackLoader.availableScripts.size()) {
                this.selectedIndex = clickedIndex;

                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < 500 && clickedIndex == lastClickedIndex) {
                    saveAndClose();
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

    private void updateScrollFromMouse(int mouseY) {
        int listHeight = listBottom - listTop;
        float ratio = (float)(mouseY - listTop) / (float)listHeight;
        this.scrollAmount = ratio * maxScroll;
        this.scrollAmount = MathHelper.clamp_float(this.scrollAmount, 0.0F, (float)maxScroll);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && maxScroll > 0) {
            int direction = wheel > 0 ? -1 : 1;

            this.scrollAmount += (direction * (slotHeight / 2.0F));
            this.scrollAmount = MathHelper.clamp_float(this.scrollAmount, 0.0F, (float)maxScroll);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float f) {
        this.drawGradientRect(0, 0, this.width, this.height, -1072689136, -804253680);

        drawCenteredString(fontRendererObj, "Announcer Config", width / 2, 8, 0xFFFFFF);
        drawString(fontRendererObj, "Link Key", width / 2 - 100, 25, 0xA0A0A0);
        this.linkKeyField.drawTextBox();

        int listHeight = listBottom - listTop;

        drawRect(listX, listTop, listX + listWidth, listBottom, 0x60000000);

        net.minecraft.client.gui.ScaledResolution res = new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = res.getScaleFactor();

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(listX * scale, (height - listBottom) * scale, listWidth * scale, listHeight * scale);

        // アイテム描画ループ
        List<AnnounceScriptInfo> scripts = AnnouncePackLoader.availableScripts;
        for (int i = 0; i < scripts.size(); i++) {
            int slotY = listTop + (i * slotHeight) - (int)scrollAmount;

            if (slotY + slotHeight < listTop) continue;
            if (slotY > listBottom) break;

            AnnounceScriptInfo info = scripts.get(i);

            if (i == selectedIndex) {
                drawRect(listX, slotY, listX + listWidth, slotY + slotHeight, 0x40FFFFFF);
            }

            if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= slotY && mouseY < slotY + slotHeight && mouseY >= listTop && mouseY < listBottom) {
                drawRect(listX, slotY, listX + listWidth, slotY + slotHeight, 0x20FFFFFF);
            }

            drawString(fontRendererObj, info.displayName, listX + 5, slotY + 2, 0xFFFFFF);
            drawString(fontRendererObj, "(" + info.fileName + ")", listX + 5, slotY + 12, 0x808080);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        if (maxScroll > 0) {
            int scrollBarX = listX + listWidth + 2;
            int scrollBarWidth = 6;

            drawRect(scrollBarX, listTop, scrollBarX + scrollBarWidth, listBottom, 0x40000000);

            int knobHeight = (int) ((float)listHeight / (float)contentHeight * listHeight);
            knobHeight = MathHelper.clamp_int(knobHeight, 10, listHeight); // 最低10px

            int knobY = listTop + (int) (scrollAmount / maxScroll * (listHeight - knobHeight));

            drawRect(scrollBarX, knobY, scrollBarX + scrollBarWidth, knobY + knobHeight, 0xFFFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, f);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }
}
