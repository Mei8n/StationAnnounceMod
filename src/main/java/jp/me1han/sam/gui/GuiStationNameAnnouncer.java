package jp.me1han.sam.gui;

import cpw.mods.fml.client.config.GuiButtonExt;
import jp.me1han.sam.AnnouncePackLoader;
import jp.me1han.sam.api.ScriptType;
import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.network.PacketLimits;
import jp.me1han.sam.network.PacketStationNameConfig;
import jp.me1han.sam.render.TileEntityStationNameAnnouncer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

public final class GuiStationNameAnnouncer extends GuiScriptConfig {
    private final TileEntityStationNameAnnouncer tile;
    private GuiTextField linkKey;
    private GuiTextField script;
    public GuiStationNameAnnouncer(TileEntityStationNameAnnouncer tile) { this.tile=tile; }
    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true); buttonList.clear();
        int left=(width-240)/2, top=(height-130)/2;
        linkKey=new GuiTextField(fontRendererObj,left+10,top+25,220,18);
        linkKey.setMaxStringLength(PacketLimits.LINK_KEY); linkKey.setText(tile.getLinkKey());
        script=new GuiTextField(fontRendererObj,left+10,top+65,220,18);
        script.setMaxStringLength(PacketLimits.NAME); script.setText(tile.getScriptName());
        buttonList.add(new GuiButtonExt(0,left+10,top+105,220,20,I18n.format("gui.done")));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id!=0)return;
        String name=script.getText().trim();
        if(!AnnouncePackLoader.canUseScript(name,ScriptType.STATION_NAME))return;
        PacketStationNameConfig packet=new PacketStationNameConfig(tile.xCoord,tile.yCoord,tile.zCoord,linkKey.getText(),name);
        if(!packet.isValidPayload())return;
        NetworkHandler.INSTANCE.sendToServer(packet); tile.applyConfig(linkKey.getText(),name); mc.thePlayer.closeScreen();
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground(); int left=(width-240)/2,top=(height-130)/2;
        drawCenteredString(fontRendererObj,I18n.format("gui.sam.station_name.title"),width/2,top,0xffffff);
        drawString(fontRendererObj,I18n.format("gui.sam.link_key"),left+10,top+14,0xa0a0a0);
        drawString(fontRendererObj,I18n.format("gui.sam.station_name.script"),left+10,top+54,0xa0a0a0);
        linkKey.drawTextBox(); script.drawTextBox(); super.drawScreen(mouseX,mouseY,partial);
        drawScriptDisplayName(script.getText(),ScriptType.STATION_NAME,left+10,top+87,mouseX,mouseY);
    }
    @Override protected void keyTyped(char c,int key) {
        if(key==Keyboard.KEY_ESCAPE){mc.thePlayer.closeScreen();return;}
        if(linkKey.textboxKeyTyped(c,key)||script.textboxKeyTyped(c,key))return; super.keyTyped(c,key);
    }
    @Override protected void mouseClicked(int x,int y,int button) {
        super.mouseClicked(x,y,button); linkKey.mouseClicked(x,y,button); script.mouseClicked(x,y,button);
    }
    @Override public void updateScreen(){linkKey.updateCursorCounter();script.updateCursorCounter();}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
