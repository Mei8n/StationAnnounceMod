package jp.me1han.sam;
import jp.me1han.sam.api.AnnounceData;
import java.util.ArrayList;
import java.util.List;

public class SAMScriptAPI {

    public jp.me1han.sam.api.DepartureProgram push() {
        return new jp.me1han.sam.api.DepartureProgram(false);
    }

    public jp.me1han.sam.api.DepartureProgram toggle() {
        return new jp.me1han.sam.api.DepartureProgram(true);
    }

    public jp.me1han.sam.api.DepartureInterval interval(double seconds) {
        return new jp.me1han.sam.api.DepartureInterval(seconds);
    }

    public String startmelo(String id) {
        return (id == null || id.isEmpty()) ? null : id;
    }
    public String arrmelo(String id) {
        return (id == null || id.isEmpty()) ? null : id;
    }

    public AnnounceData build(String start, List<Object> body, String loop) {
        return build(start, body, loop, 1);
    }

    public Object build(String start, List<Object> body, Object ending) {
        if (ending instanceof jp.me1han.sam.api.DepartureProgram) {
            jp.me1han.sam.api.DepartureProgram mode = (jp.me1han.sam.api.DepartureProgram) ending;
            mode.melody = clean(start);
            mode.doorCloseSounds.clear();
            mode.doorCloseIntervalTicks.clear();
            copyDepartureParts(body, mode);
            return mode;
        }
        List<String> sounds = new ArrayList<>();
        copySounds(body, sounds, "Announcement");
        return new AnnounceData(start, sounds, ending == null ? null : ending.toString());
    }

    public AnnounceData build(String start, List<Object> body, String loop, int repeatCount) {
        List<String> sounds = new ArrayList<>();
        copySounds(body, sounds, "Announcement");
        return new AnnounceData(start, sounds, loop, repeatCount);
    }

    private static void copySounds(List<Object> source, List<String> target, String type) {
        if (source == null) return;
        if (source.size() > 256) throw new IllegalArgumentException("Too many " + type.toLowerCase() + " sounds (maximum 256)");
        for (Object sound : source) {
            if (sound instanceof jp.me1han.sam.api.DepartureInterval)
                throw new IllegalArgumentException("sam.interval() is only available in departure announcements");
            String id = clean(sound == null ? null : sound.toString());
            if (id.isEmpty()) throw new IllegalArgumentException(type + " sound ID must not be empty");
            target.add(id);
        }
    }

    private static void copyDepartureParts(List<Object> source, jp.me1han.sam.api.DepartureProgram mode) {
        if (source == null) return;
        if (source.size() > 256) throw new IllegalArgumentException("Too many door-close parts (maximum 256)");
        for (Object part : source) {
            if (part instanceof jp.me1han.sam.api.DepartureInterval) {
                mode.doorCloseSounds.add("");
                mode.doorCloseIntervalTicks.add(((jp.me1han.sam.api.DepartureInterval) part).ticks);
            } else {
                String id = clean(part == null ? null : part.toString());
                if (id.isEmpty()) throw new IllegalArgumentException("Door-close sound ID must not be empty");
                mode.doorCloseSounds.add(id);
                mode.doorCloseIntervalTicks.add(0);
            }
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
