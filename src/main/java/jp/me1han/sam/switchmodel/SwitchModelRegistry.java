package jp.me1han.sam.switchmodel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import jp.me1han.sam.StationAnnounceModCore;

public final class SwitchModelRegistry {
    public static final String DEFAULT_MODEL = "sam_push";
    private static final String ROOT = "assets/stationannouncemod/switches/";
    private static final Map<String, SwitchModelDefinition> MODELS = new LinkedHashMap<>();
    public static void reset() {
        MODELS.clear();
        for (String name : new String[]{"push.json", "alternate.json"}) {
            load(SwitchModelRegistry.class.getResourceAsStream("/" + ROOT + name), "stationannouncemod:switches/" + name);
        }
    }
    public static void loadPack(ZipFile zip) throws IOException {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(ROOT) && entry.getName().endsWith(".json")) {
                load(zip.getInputStream(entry), "stationannouncemod:" + entry.getName().substring("assets/stationannouncemod/".length()));
            }
        }
    }
    private static void load(InputStream stream, String resource) {
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            SwitchModelDefinition model = SwitchModelDefinition.parse(reader, resource);
            if (MODELS.containsKey(model.name)) throw new IllegalArgumentException("Duplicate switch model: " + model.name);
            MODELS.put(model.name, model);
        } catch (Exception e) { StationAnnounceModCore.logger.error("[SAM] Invalid switch model " + resource, e); }
    }
    public static SwitchModelDefinition get(String name) { return MODELS.get(name); }
    public static SwitchModelDefinition getOrDefault(String name) {
        SwitchModelDefinition model = get(name);
        return model == null ? get(DEFAULT_MODEL) : model;
    }
    public static List<SwitchModelDefinition> list() {
        List<SwitchModelDefinition> result = new ArrayList<>(MODELS.values());
        result.sort(Comparator.comparing(model -> model.displayName));
        return result;
    }
}
