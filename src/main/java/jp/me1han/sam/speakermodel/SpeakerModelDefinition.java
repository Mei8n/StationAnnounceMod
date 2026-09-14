package jp.me1han.sam.speakermodel;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;
import jp.me1han.sam.switchmodel.StaticModelDefinition;
import jp.me1han.sam.switchmodel.SwitchModelDefinition;
import jp.me1han.sam.switchmodel.ModelTextureResolver;

/** Common-side metadata for a static Speaker MQO model. */
public final class SpeakerModelDefinition implements StaticModelDefinition {
    private static final double[] ZERO_OFFSET = {0, 0, 0};
    public String name;
    public String displayName;
    public String tags = "";
    public String modelFile;
    public double scale = 0.01;
    public double[] modelOffset = {0, 0, 0};
    public boolean smoothing;
    public boolean doCulling;
    public double[] bounds = {0.25, 0, 0.25, 0.75, 1, 0.75};
    public final Map<String, String> textures = new LinkedHashMap<>();

    public static SpeakerModelDefinition parse(Reader reader, String resource) {
        JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
        SpeakerModelDefinition result = new SpeakerModelDefinition();
        result.name = string(json, "name", "");
        if (!result.name.matches("[a-zA-Z0-9_.:-]{1,128}")) throw new IllegalArgumentException("Invalid speaker model name");
        result.displayName = string(json, "displayName", result.name);
        result.tags = string(json, "tags", "");
        JsonObject model = json.getAsJsonObject("model");
        if (model == null) throw new IllegalArgumentException("Missing model object");
        result.modelFile = SwitchModelDefinition.resolveResource(resource, string(model, "modelFile", ""));
        if (!result.modelFile.endsWith(".mqo")) throw new IllegalArgumentException("modelFile must be an .mqo file");
        if (model.has("scale")) result.scale = model.get("scale").getAsDouble();
        if (!finite(result.scale) || result.scale <= 0 || result.scale > 100) throw new IllegalArgumentException("Invalid model scale");
        if (model.has("offset")) result.modelOffset = vector(model.getAsJsonArray("offset"), 3);
        if (json.has("smoothing")) result.smoothing = json.get("smoothing").getAsBoolean();
        if (json.has("doCulling")) result.doCulling = json.get("doCulling").getAsBoolean();
        ModelTextureResolver.parse(model, resource, result.textures);
        if (json.has("bounds")) result.bounds = vector(json.getAsJsonArray("bounds"), 6);
        for (int i = 0; i < 6; i++) if (Math.abs(result.bounds[i]) > 16)
            throw new IllegalArgumentException("Speaker model bounds exceed 16 blocks");
        for (int i = 0; i < 3; i++) if (result.bounds[i] >= result.bounds[i + 3])
            throw new IllegalArgumentException("Invalid speaker model bounds");
        return result;
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString().trim() : fallback;
    }
    private static double[] vector(JsonArray values, int count) {
        if (values == null || values.size() != count) throw new IllegalArgumentException("Expected " + count + " coordinates");
        double[] result = new double[count];
        for (int i = 0; i < count; i++) {
            result[i] = values.get(i).getAsDouble();
            if (!finite(result[i]) || Math.abs(result[i]) > 100000) throw new IllegalArgumentException("Invalid coordinate");
        }
        return result;
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }

    @Override public String getName() { return name; }
    @Override public String getModelFile() { return modelFile; }
    @Override public double getScale() { return scale; }
    @Override public double[] getModelOffset() { return modelOffset; }
    @Override public boolean isSmoothing() { return smoothing; }
    @Override public boolean isCulling() { return doCulling; }
    @Override public Map<String, String> getTextures() { return textures; }
    @Override public void validateParts(Set<String> parts) { if (parts.isEmpty()) throw new IllegalArgumentException("MQO has no parts"); }
    @Override public boolean visible(String part, boolean state) { return true; }
    @Override public double[] partOffset(String part, boolean state) { return ZERO_OFFSET; }
}
