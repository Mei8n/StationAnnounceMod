package jp.me1han.sam.switchmodel;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;

/** Common-side JSON data. Does not depend on RTM or OpenGL. Offsets use MQO model units. */
public final class SwitchModelDefinition {
    public enum SwitchMode { ALTERNATE, MOMENTARY }
    public SwitchMode switchMode = SwitchMode.MOMENTARY;
    public String name;
    public String displayName;
    public String tags = "";
    public String modelFile;
    public String buttonTexture = "";
    public String soundOn = "";
    public String soundOff = "";
    public double scale = 0.01;
    public double[] modelOffset = {0, 0, 0};
    public double[] bounds = {0.25, 0, 0.25, 0.75, 0.3, 0.75};
    public final Map<String, String> textures = new LinkedHashMap<>();
    public final Set<String> normalParts = new LinkedHashSet<>();
    public final Set<String> pressedParts = new LinkedHashSet<>();
    public final Map<String, double[]> translations = new LinkedHashMap<>();

    public static SwitchModelDefinition parse(Reader reader, String resource) {
        JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
        SwitchModelDefinition result = new SwitchModelDefinition();
        result.name = string(json, "name", "");
        if (!result.name.matches("[a-zA-Z0-9_.:-]{1,128}")) throw new IllegalArgumentException("Invalid switch model name");
        result.displayName = string(json, "displayName", result.name);
        String mode = string(json, "switchMode", "momentary");
        if ("alternate".equals(mode)) result.switchMode = SwitchMode.ALTERNATE;
        else if (!"momentary".equals(mode)) throw new IllegalArgumentException("Invalid switchMode: " + mode);
        result.tags = string(json, "tags", "");
        JsonObject model = json.getAsJsonObject("model");
        result.modelFile = resolveResource(resource, string(model, "modelFile", ""));
        if (!result.modelFile.endsWith(".mqo")) throw new IllegalArgumentException("modelFile must be an .mqo file");
        if (model.has("scale")) result.scale = model.get("scale").getAsDouble();
        if (model.has("offset")) result.modelOffset = vector(model.getAsJsonArray("offset"), 3);
        if (!Double.isFinite(result.scale) || result.scale <= 0 || result.scale > 100) throw new IllegalArgumentException("Invalid model scale");
        if (model.has("textures")) {
            for (JsonElement element : model.getAsJsonArray("textures")) {
                JsonArray entry = element.getAsJsonArray();
                if (entry.size() < 2) throw new IllegalArgumentException("textures entries require material and resource");
                result.textures.put(entry.get(0).getAsString(), resolveResource(resource, entry.get(1).getAsString()));
            }
        }
        if (json.has("buttonTexture")) result.buttonTexture = optionalResource(resource, string(json, "buttonTexture", ""));
        if (json.has("sounds")) {
            JsonObject sounds = json.getAsJsonObject("sounds");
            result.soundOn = string(sounds, "on", "");
            result.soundOff = string(sounds, "off", "");
        }
        if (json.has("bounds")) {
            result.bounds = vector(json.getAsJsonArray("bounds"), 6);
            for (int i = 0; i < 3; i++) {
                if (result.bounds[i] < 0 || result.bounds[i + 3] > 1 || result.bounds[i] >= result.bounds[i + 3]) {
                    throw new IllegalArgumentException("bounds must fit inside one block");
                }
            }
        }
        if (json.has("pressedState")) {
            JsonObject state = json.getAsJsonObject("pressedState");
            names(state, "normalParts", result.normalParts);
            names(state, "pressedParts", result.pressedParts);
            if (!Collections.disjoint(result.normalParts, result.pressedParts)) throw new IllegalArgumentException("Normal and pressed parts must differ");
            if (state.has("translations")) {
                for (JsonElement element : state.getAsJsonArray("translations")) {
                    JsonObject translation = element.getAsJsonObject();
                    double[] offset = vector(translation.getAsJsonArray("offset"), 3);
                    Set<String> parts = new LinkedHashSet<>();
                    names(translation, "parts", parts);
                    if (parts.isEmpty()) throw new IllegalArgumentException("Translation requires parts");
                    for (String part : parts) {
                        if (result.translations.put(part, offset) != null) throw new IllegalArgumentException("Duplicate translation: " + part);
                    }
                }
            }
        }
        return result;
    }

    public boolean visible(String part, boolean pressed) {
        return pressed ? !normalParts.contains(part) : !pressedParts.contains(part);
    }

    public double[] offset(String part, boolean pressed) {
        return pressed && translations.containsKey(part) ? translations.get(part) : new double[3];
    }

    public void validateParts(Set<String> parts) {
        Set<String> references = new LinkedHashSet<>(normalParts);
        references.addAll(pressedParts);
        references.addAll(translations.keySet());
        for (String part : references) if (!parts.contains(part)) throw new IllegalArgumentException("Unknown MQO part: " + part);
    }

    public static String resolveResource(String base, String path) {
        path = path.trim().replace('\\', '/');
        if (path.isEmpty() || path.startsWith("/") || path.contains("..")) throw new IllegalArgumentException("Invalid resource path: " + path);
        String value;
        if (path.contains(":")) value = path;
        else value = base.substring(0, base.lastIndexOf('/') + 1) + path;
        if (!value.matches("[a-z0-9_.-]+:[a-zA-Z0-9_./-]+")) throw new IllegalArgumentException("Invalid resource: " + value);
        return value;
    }

    private static String optionalResource(String base, String path) { return path.isEmpty() ? "" : resolveResource(base, path); }
    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString().trim() : fallback;
    }
    private static void names(JsonObject json, String key, Set<String> result) {
        if (json.has(key)) for (JsonElement name : json.getAsJsonArray(key)) {
            String value = name.getAsString();
            if (value.isEmpty()) throw new IllegalArgumentException("Empty part name");
            result.add(value);
        }
    }
    private static double[] vector(JsonArray values, int count) {
        if (values == null || values.size() != count) throw new IllegalArgumentException("Expected " + count + " coordinates");
        double[] result = new double[count];
        for (int i = 0; i < count; i++) {
            result[i] = values.get(i).getAsDouble();
            if (!Double.isFinite(result[i]) || Math.abs(result[i]) > 100000) throw new IllegalArgumentException("Invalid coordinate");
        }
        return result;
    }
}
