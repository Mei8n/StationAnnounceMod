package jp.me1han.sam.speakermodel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import jp.me1han.sam.StationAnnounceModCore;

/** Speaker-only model namespace. Empty is valid and means that only No Model is available. */
public final class SpeakerModelRegistry {
    public static final String ROOT = "assets/stationannouncemod/speakers/";
    private static final Map<String, SpeakerModelDefinition> MODELS = new LinkedHashMap<>();
    private SpeakerModelRegistry() {}
    public static void reset() { MODELS.clear(); }
    public static void loadPack(ZipFile zip) throws IOException {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(ROOT) && entry.getName().endsWith(".json"))
                load(zip.getInputStream(entry), "stationannouncemod:" + entry.getName().substring("assets/stationannouncemod/".length()));
        }
    }
    static void load(InputStream stream, String resource) {
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            SpeakerModelDefinition model = SpeakerModelDefinition.parse(reader, resource);
            if (MODELS.containsKey(model.name)) throw new IllegalArgumentException("Duplicate speaker model: " + model.name);
            MODELS.put(model.name, model);
        } catch (Exception e) { StationAnnounceModCore.logger.error("[SAM] Invalid speaker model " + resource, e); }
    }
    public static SpeakerModelDefinition get(String name) { return MODELS.get(name); }
    public static List<SpeakerModelDefinition> list() {
        List<SpeakerModelDefinition> result = new ArrayList<>(MODELS.values());
        result.sort(Comparator.comparing(model -> model.displayName));
        return result;
    }
}
