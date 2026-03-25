package jp.me1han.sam.gui;

import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.container.ContainerAnnouncer;
import jp.me1han.sam.network.MessageConfig;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;

public class GuiAnnouncer extends GuiScreen {
    private int selectedIndex = 0;

    private ContainerAnnouncer container;
    private TileEntityAnnouncer tile;

    public GuiAnnouncer(ContainerAnnouncer container, TileEntityAnnouncer tile) {
        this.container = container;
        this.tile = tile;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        String label = AnnouncePackLoader.availableScripts.isEmpty() ? "No Scripts" : "Script: " + AnnouncePackLoader.availableScripts.get(selectedIndex).displayName;
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height / 2 - 10, label));
        this.buttonList.add(new GuiButton(1, this.width / 2 - 100, this.height / 2 + 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0 && !AnnouncePackLoader.availableScripts.isEmpty()) {
            selectedIndex = (selectedIndex + 1) % AnnouncePackLoader.availableScripts.size();
            button.displayString = "Script: " + AnnouncePackLoader.availableScripts.get(selectedIndex).displayName;
        } else if (button.id == 1) {
            if (!AnnouncePackLoader.availableScripts.isEmpty()) {
                // サーバーへ設定を送信
                String selectedFile = AnnouncePackLoader.availableScripts.get(selectedIndex).fileName;
                NetworkHandler.INSTANCE.sendToServer(new MessageConfig(tile.xCoord, tile.yCoord, tile.zCoord, selectedFile));
            }
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int x, int y, float f) {
        this.drawDefaultBackground();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawCenteredString(this.fontRendererObj, "Station Announcer Settings", this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        super.drawScreen(x, y, f);
    }
}
