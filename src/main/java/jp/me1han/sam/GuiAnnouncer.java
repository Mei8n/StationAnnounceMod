package jp.me1han.sam;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;

public class GuiAnnouncer extends GuiScreen {
    private final TileEntityAnnouncer tile;
    private int selectedIndex = 0;

    public GuiAnnouncer(TileEntityAnnouncer tile) {
        this.tile = tile;
        // 初期選択状態を合わせる
        for (int i = 0; i < PackLoader.availableScripts.size(); i++) {
            if (PackLoader.availableScripts.get(i).fileName.equals(tile.getScriptName())) {
                selectedIndex = i;
                break;
            }
        }
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        String label = PackLoader.availableScripts.isEmpty() ? "No Scripts" : "Script: " + PackLoader.availableScripts.get(selectedIndex).displayName;
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height / 2 - 10, label));
        this.buttonList.add(new GuiButton(1, this.width / 2 - 100, this.height / 2 + 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0 && !PackLoader.availableScripts.isEmpty()) {
            selectedIndex = (selectedIndex + 1) % PackLoader.availableScripts.size();
            button.displayString = "Script: " + PackLoader.availableScripts.get(selectedIndex).displayName;
        } else if (button.id == 1) {
            if (!PackLoader.availableScripts.isEmpty()) {
                // サーバーへ設定を送信
                String selectedFile = PackLoader.availableScripts.get(selectedIndex).fileName;
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
