package jp.me1han.sam.gui;

import cpw.mods.fml.client.config.GuiCheckBox;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.network.PacketAwarenessConfig;
import jp.me1han.sam.render.TileEntityAwarenessAnnouncer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

public class GuiAwarenessAnnouncer extends GuiScreen {
    private final TileEntityAwarenessAnnouncer tile;
    private GuiTextField linkKeyField;
    private GuiTextField soundListField;
    private GuiTextField intervalField;
    private GuiTextField departureDelayField;
    private GuiCheckBox randomOrderCheck;
    private GuiCheckBox allowOverlapCheck;
    private GuiCheckBox playAfterDepartureCheck;

    public GuiAwarenessAnnouncer(TileEntityAwarenessAnnouncer tile) {
        this.tile = tile;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int left = this.width / 2 - 120;
        int top = this.height / 2 - 120;

        this.linkKeyField = new GuiTextField(this.fontRendererObj, left, top + 20, 240, 20);
        this.linkKeyField.setMaxStringLength(64);
        this.linkKeyField.setText(this.tile.linkKey == null ? "" : this.tile.linkKey);

        this.soundListField = new GuiTextField(this.fontRendererObj, left, top + 60, 240, 20);
        this.soundListField.setMaxStringLength(2048);
        this.soundListField.setText(this.tile.soundList == null ? "" : this.tile.soundList);

        this.intervalField = new GuiTextField(this.fontRendererObj, left, top + 100, 110, 20);
        this.intervalField.setText(formatSeconds(this.tile.intervalTicks));

        this.departureDelayField = new GuiTextField(this.fontRendererObj, left + 130, top + 100, 110, 20);
        this.departureDelayField.setText(formatSeconds(this.tile.departureDelayTicks));

        this.randomOrderCheck = new GuiCheckBox(1, left, top + 130, I18n.format("gui.sam.awareness.random_order"), this.tile.randomOrder);
        this.allowOverlapCheck = new GuiCheckBox(2, left, top + 150, I18n.format("gui.sam.awareness.allow_overlap"), this.tile.allowOverlap);
        this.playAfterDepartureCheck = new GuiCheckBox(3, left, top + 170, I18n.format("gui.sam.awareness.after_departure"), this.tile.playAfterDeparture);
        this.buttonList.add(this.randomOrderCheck);
        this.buttonList.add(this.allowOverlapCheck);
        this.buttonList.add(this.playAfterDepartureCheck);
        this.buttonList.add(new GuiButton(0, left, top + 200, 240, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id != 0) {
            return;
        }

        try {
            int intervalTicks = secondsToTicks(this.intervalField.getText(), 60.0D, 20);
            int departureDelayTicks = secondsToTicks(this.departureDelayField.getText(), 5.0D, 0);
            String linkKey = this.linkKeyField.getText() == null ? "" : this.linkKeyField.getText().trim();
            String soundList = this.soundListField.getText() == null ? "" : this.soundListField.getText().trim();

            NetworkHandler.INSTANCE.sendToServer(new PacketAwarenessConfig(
                this.tile.xCoord, this.tile.yCoord, this.tile.zCoord, linkKey, soundList, intervalTicks,
                this.randomOrderCheck.isChecked(), this.allowOverlapCheck.isChecked(),
                this.playAfterDepartureCheck.isChecked(), departureDelayTicks
            ));
            this.tile.applyConfig(linkKey, soundList, intervalTicks, this.randomOrderCheck.isChecked(),
                this.allowOverlapCheck.isChecked(), this.playAfterDepartureCheck.isChecked(), departureDelayTicks);
            this.mc.thePlayer.closeScreen();
        } catch (NumberFormatException e) {
            StationAnnounceModCore.logger.warn("[SAM] Interval and delay must be numbers", e);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int left = this.width / 2 - 120;
        int top = this.height / 2 - 120;

        drawCenteredString(this.fontRendererObj, I18n.format("gui.sam.awareness.title"), this.width / 2, top, 0xFFFFFF);
        drawString(this.fontRendererObj, I18n.format("gui.sam.link_key"), left, top + 10, 0xA0A0A0);
        drawString(this.fontRendererObj, I18n.format("gui.sam.awareness.sound_ids"), left, top + 50, 0xA0A0A0);
        drawString(this.fontRendererObj, I18n.format("gui.sam.awareness.interval"), left, top + 90, 0xA0A0A0);
        drawString(this.fontRendererObj, I18n.format("gui.sam.awareness.departure_delay"), left + 130, top + 90, 0xA0A0A0);

        this.linkKeyField.drawTextBox();
        this.soundListField.drawTextBox();
        this.intervalField.drawTextBox();
        this.departureDelayField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char c, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.thePlayer.closeScreen();
            return;
        }
        if (this.linkKeyField.textboxKeyTyped(c, keyCode)) return;
        if (this.soundListField.textboxKeyTyped(c, keyCode)) return;
        if (this.intervalField.textboxKeyTyped(c, keyCode)) return;
        if (this.departureDelayField.textboxKeyTyped(c, keyCode)) return;
        super.keyTyped(c, keyCode);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        this.linkKeyField.mouseClicked(x, y, button);
        this.soundListField.mouseClicked(x, y, button);
        this.intervalField.mouseClicked(x, y, button);
        this.departureDelayField.mouseClicked(x, y, button);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int secondsToTicks(String value, double fallback, int minimumTicks) {
        String normalized = value == null ? "" : value.trim().replace(',', '.');
        double seconds = normalized.isEmpty() ? fallback : Double.parseDouble(normalized);
        if (Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            throw new NumberFormatException("Invalid seconds");
        }
        return Math.max(minimumTicks, (int) Math.ceil(Math.max(0.0D, seconds) * 20.0D));
    }

    private String formatSeconds(int ticks) {
        double seconds = Math.max(0, ticks) / 20.0D;
        return seconds == Math.floor(seconds) ? String.valueOf((int) seconds) : String.valueOf(seconds);
    }
}
