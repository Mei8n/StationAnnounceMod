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
import net.minecraft.util.MathHelper;
import cpw.mods.fml.client.config.GuiCheckBox; // ★追加
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class GuiAnnouncer extends GuiScreen {
    private final ContainerAnnouncer container;
    private final TileEntityAnnouncer tile;
    private GuiTextField linkKeyField;
    private GuiCheckBox chkPlayLocal; // ★追加
    private int selectedIndex = -1;

    private final int slotHeight = 24;
    private int listTop;
    private int listBottom;
    private final int listWidth = 220;
    private int listX;

    private float scrollAmount = 0.0F;
    private boolean isScrollingBar = false;
    private int contentHeight;
    private int maxScroll;

    public GuiAnnouncer(ContainerAnnouncer container, TileEntityAnnouncer tile) {
        this.container = container;
        this.tile = tile;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int guiWidth = 240;
        int guiHeight = 220;
        int left = (this.width - guiWidth) / 2;
        int top = (this.height - guiHeight) / 2;

        this.linkKeyField = new GuiTextField(fontRendererObj, left + 10, top + 20, 220, 16);
        this.linkKeyField.setText(tile.linkKey);
        this.linkKeyField.setMaxStringLength(32);

        this.chkPlayLocal = new GuiCheckBox(1, left + 10, top + 45, "Play sound from this block", tile.playLocalSound);
        this.buttonList.add(chkPlayLocal);

        this.listX = left + 10;
        this.listTop = top + 75;    // リストの開始位置
        this.listBottom = top + 185; // リストの終了位置

        this.buttonList.add(new GuiButton(0, left + 10, top + 195, 220, 20, "Done"));

        String currentScript = tile.getScriptName();
        List<AnnounceScriptInfo> scripts = AnnouncePackLoader.availableScripts;
        for (int i = 0; i < scripts.size(); i++) {
            if (scripts.get(i).fileName.equals(currentScript)) {
                this.selectedIndex = i;
                break;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            String scriptName = "";
            if (selectedIndex >= 0 && selectedIndex < AnnouncePackLoader.availableScripts.size()) {
                scriptName = AnnouncePackLoader.availableScripts.get(selectedIndex).fileName;
            }

             NetworkHandler.INSTANCE.sendToServer(new PacketConfig(
                tile.xCoord, tile.yCoord, tile.zCoord,
                scriptName,
                linkKeyField.getText(),
                chkPlayLocal.isChecked()
            ));

            this.tile.setScriptName(scriptName);
            this.tile.linkKey = this.linkKeyField.getText();
            this.tile.playLocalSound = this.chkPlayLocal.isChecked();

            this.mc.thePlayer.closeScreen();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int left = (this.width - 240) / 2;
        int top = (this.height - 220) / 2;

        drawString(fontRendererObj, "Link Key", left + 10, top + 8, 0xA0A0A0);
        drawString(fontRendererObj, "Announce Scripts", left + 10, top + 65, 0xA0A0A0);

        this.linkKeyField.drawTextBox();

        drawScriptList(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawScriptList(int mouseX, int mouseY) {
        List<AnnounceScriptInfo> scripts = AnnouncePackLoader.availableScripts;
        this.contentHeight = scripts.size() * slotHeight;
        int listHeight = listBottom - listTop;
        this.maxScroll = Math.max(0, contentHeight - listHeight);
        this.scrollAmount = MathHelper.clamp_float(scrollAmount, 0, maxScroll);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int scale = new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glScissor(listX * scale, (height - listBottom) * scale, listWidth * scale, listHeight * scale);

        drawRect(listX, listTop, listX + listWidth, listBottom, 0x80000000);

        for (int i = 0; i < scripts.size(); i++) {
            int slotY = listTop + (i * slotHeight) - (int)scrollAmount;
            if (slotY + slotHeight <= listTop) continue;
            if (slotY > listBottom) break;

            AnnounceScriptInfo info = scripts.get(i);
            if (i == selectedIndex) {
                drawRect(listX, slotY, listX + listWidth, slotY + slotHeight, 0x60FFFFFF);
            }

            drawString(fontRendererObj, info.displayName, listX + 5, slotY + 3, 0xFFFFFF);
            drawString(fontRendererObj, "(" + info.fileName + ")", listX + 5, slotY + 13, 0x808080);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
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

        if (x >= listX && x < listX + listWidth && y >= listTop && y < listBottom) {
            int clickedIndex = (y - listTop + (int)scrollAmount) / slotHeight;
            if (clickedIndex >= 0 && clickedIndex < AnnouncePackLoader.availableScripts.size()) {
                this.selectedIndex = clickedIndex;
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            if (wheel > 0) scrollAmount -= slotHeight;
            else scrollAmount += slotHeight;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
