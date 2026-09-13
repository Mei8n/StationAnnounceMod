package jp.me1han.sam.gui;

import cpw.mods.fml.client.config.GuiCheckBox;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.api.AwarenessMode;
import jp.me1han.sam.api.ScriptType;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.network.PacketAwarenessConfig;
import jp.me1han.sam.network.PacketLimits;
import jp.me1han.sam.render.TileEntityAwarenessAnnouncer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

public class GuiAwarenessAnnouncer extends GuiScriptConfig {
    private final TileEntityAwarenessAnnouncer tile;
    private AwarenessMode mode;
    private GuiTextField linkKeyField;
    private GuiTextField soundListField;
    private GuiTextField scriptNameField;
    private GuiTextField intervalField;
    private GuiTextField departureDelayField;
    private GuiCheckBox randomOrderCheck;
    private GuiCheckBox allowOverlapCheck;
    private GuiCheckBox playAfterDepartureCheck;
    private GuiButton modeButton;

    public GuiAwarenessAnnouncer(TileEntityAwarenessAnnouncer tile) {
        this.tile = tile;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.mode = this.tile.mode == null ? AwarenessMode.DIRECT : this.tile.mode;

        int left = this.width / 2 - 120;
        int top = this.height / 2 - 120;

        this.modeButton = new GuiButton(4, left, top + 15, 240, 20, "");
        this.buttonList.add(this.modeButton);

        this.linkKeyField = new GuiTextField(this.fontRendererObj, left, top + 52, 240, 20);
        this.linkKeyField.setMaxStringLength(PacketLimits.LINK_KEY);
        this.linkKeyField.setText(this.tile.getLinkKey());

        this.soundListField = new GuiTextField(this.fontRendererObj, left, top + 89, 240, 20);
        this.soundListField.setMaxStringLength(PacketLimits.SOUND_LIST);
        this.soundListField.setText(this.tile.soundList == null ? "" : this.tile.soundList);

        this.scriptNameField = new GuiTextField(this.fontRendererObj, left, top + 89, 240, 20);
        this.scriptNameField.setMaxStringLength(PacketLimits.NAME);
        this.scriptNameField.setText(this.tile.scriptName == null ? "" : this.tile.scriptName);

        this.intervalField = new GuiTextField(this.fontRendererObj, left, top + 135, 110, 20);
        this.intervalField.setText(formatSeconds(this.tile.intervalTicks));

        this.departureDelayField = new GuiTextField(this.fontRendererObj, left + 130, top + 135, 110, 20);
        this.departureDelayField.setText(formatSeconds(this.tile.departureDelayTicks));

        this.randomOrderCheck = new GuiCheckBox(1, left, top + 163, I18n.format("gui.sam.awareness.random_order"), this.tile.randomOrder);
        this.allowOverlapCheck = new GuiCheckBox(2, left, top + 181, I18n.format("gui.sam.awareness.allow_overlap"), this.tile.allowOverlap);
        this.playAfterDepartureCheck = new GuiCheckBox(3, left, top + 199, I18n.format("gui.sam.awareness.after_departure"), this.tile.playAfterDeparture);
        this.buttonList.add(this.randomOrderCheck);
        this.buttonList.add(this.allowOverlapCheck);
        this.buttonList.add(this.playAfterDepartureCheck);
        this.buttonList.add(new GuiButton(0, left, top + 220, 240, 20, I18n.format("gui.done")));
        updateModeControls();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 4) {
            this.mode = this.mode == AwarenessMode.DIRECT ? AwarenessMode.SCRIPT : AwarenessMode.DIRECT;
            updateModeControls();
            return;
        }
        if (button.id != 0) {
            return;
        }

        try {
            int intervalTicks = secondsToTicks(this.intervalField.getText(), 60.0D, 20);
            int departureDelayTicks = secondsToTicks(this.departureDelayField.getText(), 0.0D, 0);
            String linkKey = jp.me1han.sam.link.LinkKey.normalize(this.linkKeyField.getText());
            String soundList = this.soundListField.getText() == null ? "" : this.soundListField.getText().trim();
            String scriptName = this.scriptNameField.getText() == null ? "" : this.scriptNameField.getText().trim();
            if (this.mode == AwarenessMode.SCRIPT
                && !AnnouncePackLoader.canUseScript(scriptName, ScriptType.AWARENESS)) return;

            PacketAwarenessConfig packet = new PacketAwarenessConfig(
                this.tile.xCoord, this.tile.yCoord, this.tile.zCoord, linkKey, soundList,
                this.mode, scriptName, intervalTicks,
                this.randomOrderCheck.isChecked(), this.allowOverlapCheck.isChecked(),
                this.playAfterDepartureCheck.isChecked(), departureDelayTicks
            );
            if (!packet.isValidPayload()) return;
            NetworkHandler.INSTANCE.sendToServer(packet);
            this.tile.applyConfig(this.mode, linkKey, soundList, scriptName, intervalTicks,
                this.randomOrderCheck.isChecked(),
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
        drawString(this.fontRendererObj, I18n.format("gui.sam.link_key"), left, top + 42, 0xA0A0A0);
        drawString(this.fontRendererObj, I18n.format(this.mode == AwarenessMode.DIRECT
            ? "gui.sam.awareness.sound_ids" : "gui.sam.awareness.script"), left, top + 79, 0xA0A0A0);
        drawString(this.fontRendererObj, I18n.format("gui.sam.awareness.interval"), left, top + 125, 0xA0A0A0);
        drawString(this.fontRendererObj, I18n.format("gui.sam.awareness.departure_delay"), left + 130, top + 125, 0xA0A0A0);

        this.linkKeyField.drawTextBox();
        if (this.mode == AwarenessMode.DIRECT) {
            this.soundListField.drawTextBox();
            if (this.soundListField.getText().isEmpty() && !this.soundListField.isFocused()) {
                drawString(this.fontRendererObj, I18n.format("gui.sam.awareness.sound_ids_example"),
                    left + 4, top + 95, 0x707070);
            }
        } else {
            this.scriptNameField.drawTextBox();
        }
        this.intervalField.drawTextBox();
        this.departureDelayField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.mode == AwarenessMode.SCRIPT) {
            drawScriptDisplayName(this.scriptNameField.getText(), ScriptType.AWARENESS,
                left, top + 112, mouseX, mouseY);
        }
    }

    @Override
    protected void keyTyped(char c, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.thePlayer.closeScreen();
            return;
        }
        if (this.linkKeyField.textboxKeyTyped(c, keyCode)) return;
        if (this.mode == AwarenessMode.DIRECT && this.soundListField.textboxKeyTyped(c, keyCode)) return;
        if (this.mode == AwarenessMode.SCRIPT && this.scriptNameField.textboxKeyTyped(c, keyCode)) return;
        if (this.intervalField.textboxKeyTyped(c, keyCode)) return;
        if (this.departureDelayField.textboxKeyTyped(c, keyCode)) return;
        super.keyTyped(c, keyCode);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        this.linkKeyField.mouseClicked(x, y, button);
        if (this.mode == AwarenessMode.DIRECT) this.soundListField.mouseClicked(x, y, button);
        else this.scriptNameField.mouseClicked(x, y, button);
        this.intervalField.mouseClicked(x, y, button);
        this.departureDelayField.mouseClicked(x, y, button);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        this.linkKeyField.updateCursorCounter();
        this.intervalField.updateCursorCounter();
        this.departureDelayField.updateCursorCounter();
        if (this.mode == AwarenessMode.DIRECT) this.soundListField.updateCursorCounter();
        else this.scriptNameField.updateCursorCounter();
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

    private void updateModeControls() {
        this.modeButton.displayString = I18n.format("gui.sam.awareness.mode",
            I18n.format(this.mode == AwarenessMode.DIRECT
                ? "gui.sam.awareness.mode.direct" : "gui.sam.awareness.mode.script"));
        this.randomOrderCheck.visible = this.mode == AwarenessMode.DIRECT;
        if (this.mode == AwarenessMode.DIRECT) this.scriptNameField.setFocused(false);
        else this.soundListField.setFocused(false);
    }
}
