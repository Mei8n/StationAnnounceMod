package jp.me1han.sam.switchmodel;

import com.google.gson.*;
import java.util.Map;

/** Pure JSON texture selection shared by Switch and Speaker models. */
public final class ModelTextureResolver {
    private ModelTextureResolver() {}

    /**
     * Returns a usable JSON resource, or null for Minecraft's missing texture.
     * An explicitly present but invalid exact entry never falls through to default.
     */
    public static String select(Map<String, String> textures, String material) {
        if (textures.containsKey(material)) return usable(textures.get(material));
        if (textures.containsKey("default")) return usable(textures.get("default"));
        return null;
    }

    /** Parse texture metadata without making a texture-only error fatal to the model. */
    public static void parse(JsonObject model, String baseResource, Map<String, String> target) {
        if (!model.has("textures")) return;
        JsonArray entries;
        try { entries = model.getAsJsonArray("textures"); }
        catch (RuntimeException ignored) { return; }
        for (JsonElement element : entries) {
            String material = null;
            try {
                JsonArray entry = element.getAsJsonArray();
                if (entry.size() < 2) continue;
                material = entry.get(0).getAsString();
                if (material.isEmpty()) continue;
                String path = entry.get(1).getAsString();
                try { target.put(material, SwitchModelDefinition.resolveResource(baseResource, path)); }
                catch (RuntimeException invalidPath) { target.put(material, ""); }
            } catch (RuntimeException malformed) {
                // A texture entry cannot invalidate otherwise usable geometry.
                if (material != null && !material.isEmpty()) target.put(material, "");
            }
        }
    }

    private static String usable(String resource) {
        return resource == null || resource.isEmpty() ? null : resource;
    }
}
