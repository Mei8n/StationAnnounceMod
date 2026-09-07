package jp.me1han.sam;
import jp.me1han.sam.api.AnnounceData;
import java.util.ArrayList;
import java.util.List;

public class SAMScriptAPI {

    public jp.me1han.sam.api.DepartureProgram momentary() {
        return new jp.me1han.sam.api.DepartureProgram(false);
    }

    public jp.me1han.sam.api.DepartureProgram alternate() {
        return new jp.me1han.sam.api.DepartureProgram(true);
    }

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
}
