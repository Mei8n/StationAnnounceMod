package jp.me1han.sam;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;

public class SAMJsAPI {

    public String startmelo(String id) {
        return (id == null || id.isEmpty()) ? null : id;
    }

    public String arrmelo(String id) {
        return (id == null || id.isEmpty()) ? null : id;
    }

    public AnnounceData build(String start, List<Object> body, String loop) {
        List<String> sounds = new ArrayList<>();
        if (body != null) {
            for (Object o : body) {
                sounds.add(o.toString());
            }
        }
        return new AnnounceData(start, sounds, loop);
    }

    public String getTrainData(TileEntityAnnouncer tile) {
        if (tile == null) return "";

        // 1. 放送装置のリンクキーを取得
        String linkKey = tile.getLinkKey();

        // 2. TrainDataManager から、そのキーに紐づく最新の車両データを取得
        // 列車選別装置が通過時に書き込んだデータ (String) が返ります
        String data = TrainDataManager.INSTANCE.getData(linkKey);

        return (data != null) ? data : "";
    }
}
