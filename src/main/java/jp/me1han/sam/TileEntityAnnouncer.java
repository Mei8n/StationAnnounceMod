package jp.me1han.sam;

import net.minecraft.tileentity.TileEntity;

public class TileEntityAnnouncer extends TileEntity {
    private boolean lastPowered = false;

    public void onRedstoneUpdate(boolean powered) {
        // 信号が入った瞬間（立ち上がり）だけ判定
        if (powered && !lastPowered) {
            System.out.println("放送開始命令を受信しました！");
            // ここにパケット送信やJSパースの処理を書いていきます
        }
        this.lastPowered = powered;
    }
}
