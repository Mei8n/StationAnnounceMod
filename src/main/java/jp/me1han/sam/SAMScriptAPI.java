package jp.me1han.sam;
import jp.me1han.sam.api.AnnounceData;
import java.util.ArrayList;
import java.util.List;
import jp.me1han.sam.network.PacketLimits;

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
        String value = clean(id);
        if (value.isEmpty()) return null;
        requireSoundId(value, "Start melody");
        return value;
    }
    public String arrmelo(String id) {
        String value = clean(id);
        if (value.isEmpty()) return null;
        requireSoundId(value, "Arrival melody");
        return value;
    }

    public AnnounceData build(String start, List<Object> body, String loop) {
        return build(start, body, loop, 1);
    }

    public Object build(String start, List<Object> body, Object ending) {
        if (ending instanceof jp.me1han.sam.api.DepartureProgram) {
            jp.me1han.sam.api.DepartureProgram mode = (jp.me1han.sam.api.DepartureProgram) ending;
            mode.melody = clean(start);
            requireSoundId(mode.melody, "Departure melody");
            mode.doorCloseSounds.clear();
            mode.doorCloseIntervalTicks.clear();
            copyDepartureParts(body, mode);
            return mode;
        }
        List<String> sounds = new ArrayList<>();
        List<Integer> intervals = new ArrayList<>();
        copySounds(body, sounds, intervals, "Announcement");
        String startId = optionalSoundId(start, "Start melody");
        String loopId = optionalSoundId(ending == null ? null : ending.toString(), "Arrival melody");
        return new AnnounceData(startId, sounds, intervals, loopId, 1);
    }

    public AnnounceData build(String start, List<Object> body, String loop, int repeatCount) {
        List<String> sounds = new ArrayList<>();
        List<Integer> intervals = new ArrayList<>();
        copySounds(body, sounds, intervals, "Announcement");
        return new AnnounceData(optionalSoundId(start, "Start melody"), sounds, intervals,
            optionalSoundId(loop, "Arrival melody"), repeatCount);
    }

    private static void copySounds(List<Object> source, List<String> target, List<Integer> intervals, String type) {
        if (source == null) return;
        if (source.size() > PacketLimits.BODY_SOUNDS) throw new IllegalArgumentException("Too many "
            + type.toLowerCase() + " sounds (maximum " + PacketLimits.BODY_SOUNDS + ")");
        for (Object sound : source) {
            if (sound instanceof jp.me1han.sam.api.DepartureInterval) {
                target.add("");
                intervals.add(((jp.me1han.sam.api.DepartureInterval) sound).ticks);
                continue;
            }
            String id = clean(sound == null ? null : sound.toString());
            if (id.isEmpty()) throw new IllegalArgumentException(type + " sound ID must not be empty");
            requireSoundId(id, type);
            target.add(id);
            intervals.add(0);
        }
    }

    private static void copyDepartureParts(List<Object> source, jp.me1han.sam.api.DepartureProgram mode) {
        if (source == null) return;
        if (source.size() > PacketLimits.BODY_SOUNDS) throw new IllegalArgumentException("Too many door-close parts (maximum "
            + PacketLimits.BODY_SOUNDS + ")");
        for (Object part : source) {
            if (part instanceof jp.me1han.sam.api.DepartureInterval) {
                mode.doorCloseSounds.add("");
                mode.doorCloseIntervalTicks.add(((jp.me1han.sam.api.DepartureInterval) part).ticks);
            } else {
                String id = clean(part == null ? null : part.toString());
                if (id.isEmpty()) throw new IllegalArgumentException("Door-close sound ID must not be empty");
                requireSoundId(id, "Door-close");
                mode.doorCloseSounds.add(id);
                mode.doorCloseIntervalTicks.add(0);
            }
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String optionalSoundId(String value, String label) {
        String id = clean(value);
        if (id.isEmpty()) return null;
        requireSoundId(id, label);
        return id;
    }
    private static void requireSoundId(String id, String label) {
        if (!PacketLimits.string(id, PacketLimits.NAME))
            throw new IllegalArgumentException(label + " sound ID exceeds " + PacketLimits.NAME + " characters");
    }
}
