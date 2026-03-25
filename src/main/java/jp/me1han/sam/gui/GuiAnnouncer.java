package jp.me1han.sam.gui;

import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.container.ContainerAnnouncer;
import jp.me1han.sam.network.MessageConfig;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public class GuiAnnouncer extends GuiScreen {
    private final ContainerAnnouncer container;
    private final TileEntityAnnouncer tile;
    private int selectedIndex = 0;
    private GuiTextField linkKeyField;

    public GuiAnnouncer(ContainerAnnouncer container, TileEntityAnnouncer tile) {
        this.container = container;
        this.tile = tile;

        if (tile.getScriptName() != null) {
            for (int i = 0; i < AnnouncePackLoader.availableScripts.size(); i++) {
                if (AnnouncePackLoader.availableScripts.get(i).fileName.equals(tile.getScriptName())) {
                    this.selectedIndex = i;
                    break;
                }
            }
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        this.linkKeyField = new GuiTextField(fontRendererObj, width / 2 - 100, height / 2 - 50, 200, 20);
        this.linkKeyField.setText(tile.linkKey != null ? tile.linkKey : "");
        this.linkKeyField.setMaxStringLength(32);
        this.linkKeyField.setFocused(true);

        this.buttonList.add(new GuiButton(0, width / 2 - 100, height / 2 + 10, "Select Next Script"));
        this.buttonList.add(new GuiButton(1, width / 2 - 100, height / 2 + 40, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            if (!AnnouncePackLoader.availableScripts.isEmpty()) {
                selectedIndex = (selectedIndex + 1) % AnnouncePackLoader.availableScripts.size();
            }
        } else if (button.id == 1) {
            String selectedFile = AnnouncePackLoader.availableScripts.isEmpty() ? "" : AnnouncePackLoader.availableScripts.get(selectedIndex).fileName;
            String keyToSend = linkKeyField.getText();

            NetworkHandler.INSTANCE.sendToServer(new MessageConfig(tile.xCoord, tile.yCoord, tile.zCoord, selectedFile, keyToSend));
            this.mc.thePlayer.closeScreen();
        }
    }

    @Override
    protected void keyTyped(char c, int i) {
        if (i == 1) {
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
    }

    @Override
    public void drawScreen(int x, int y, float f) {
        this.drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Announcer Config", width / 2, 20, 0xFFFFFF);
        drawString(fontRendererObj, "Link Key", width / 2 - 100, height / 2 - 65, 0xA0A0A0);
        this.linkKeyField.drawTextBox();

        String currentScript = AnnouncePackLoader.availableScripts.isEmpty() ? "No Scripts Found" : AnnouncePackLoader.availableScripts.get(selectedIndex).fileName;
        drawCenteredString(fontRendererObj, "Current: " + currentScript, width / 2, height / 2 - 15, 0xFFFFFF);

        super.drawScreen(x, y, f);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }
}
