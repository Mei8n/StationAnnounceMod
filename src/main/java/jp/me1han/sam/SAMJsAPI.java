package jp.me1han.sam;

import java.util.ArrayList;
import java.util.List;

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
        String linkKey = tile.getLinkKey(); // TileEntityAnnouncerにlinkKeyを実装する必要があります
        return TrainDataManager.INSTANCE.getData(linkKey);
    }
}
