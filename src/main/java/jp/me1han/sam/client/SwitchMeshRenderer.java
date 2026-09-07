package jp.me1han.sam.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.switchmodel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** One shared mesh cache for world, inventory and GUI rendering. */
public final class SwitchMeshRenderer implements IResourceManagerReloadListener {
    public static final SwitchMeshRenderer INSTANCE = new SwitchMeshRenderer();
    private final Map<String, MqoMesh> meshes = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    @Override public void onResourceManagerReload(IResourceManager manager) { meshes.clear(); failed.clear(); }

    public MqoMesh mesh(SwitchModelDefinition definition) {
        if (definition == null || failed.contains(definition.name)) return null;
        MqoMesh mesh = meshes.get(definition.name);
        if (mesh == null) {
            try (Reader reader = new InputStreamReader(Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation(definition.modelFile)).getInputStream(), StandardCharsets.UTF_8)) {
                mesh = MqoMesh.read(reader);
                definition.validateParts(mesh.parts.keySet());
                meshes.put(definition.name, mesh);
            } catch (Exception e) {
                failed.add(definition.name);
                StationAnnounceModCore.logger.error("[SAM] Cannot render switch model " + definition.name, e);
                return null;
            }
        }
        return mesh;
    }

    /** Coordinates are centered in X/Z; the model base is Y=0. Returns false for unavailable assets. */
    public boolean render(SwitchModelDefinition definition, boolean pressed, int brightness) {
        MqoMesh mesh = mesh(definition);
        if (mesh == null) return false;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glScaled(definition.scale, definition.scale, definition.scale);
            GL11.glTranslated(definition.modelOffset[0], definition.modelOffset[1], definition.modelOffset[2]);
            for (Map.Entry<String, List<MqoMesh.Triangle>> part : mesh.parts.entrySet()) {
                if (!definition.visible(part.getKey(), pressed)) continue;
                double[] offset = definition.offset(part.getKey(), pressed);
                GL11.glPushMatrix();
                GL11.glTranslated(offset[0], offset[1], offset[2]);
                for (int m = 0; m < mesh.materials.size(); m++) {
                    MqoMesh.Material material = mesh.materials.get(m);
                    String texture = definition.textures.get(material.name);
                    if (texture == null) texture = definition.textures.get("default");
                    if (texture == null && !material.texture.isEmpty()) {
                        // JSON overrides are authoritative, including legacy MQO absolute texture paths.
                        try { texture = SwitchModelDefinition.resolveResource(definition.modelFile, material.texture); }
                        catch (IllegalArgumentException ignored) { texture = null; }
                    }
                    if (texture == null) GL11.glDisable(GL11.GL_TEXTURE_2D);
                    else {
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                        Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation(texture));
                    }
                    Tessellator tess = Tessellator.instance;
                    tess.startDrawing(GL11.GL_TRIANGLES);
                    tess.setBrightness(brightness);
                    for (MqoMesh.Triangle face : part.getValue()) {
                        if (face.material != m) continue;
                        float shade = (float) (0.7 + 0.3 * Math.max(0, face.normal[1]));
                        tess.setColorRGBA_F((float) material.color[0] * shade, (float) material.color[1] * shade,
                            (float) material.color[2] * shade, (float) material.color[3]);
                        tess.setNormal((float) face.normal[0], (float) face.normal[1], (float) face.normal[2]);
                        for (int i = 0; i < 3; i++) tess.addVertexWithUV(face.vertices[i][0], face.vertices[i][1], face.vertices[i][2], face.uv[i][0], face.uv[i][1]);
                    }
                    tess.draw();
                }
                GL11.glPopMatrix();
            }
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
        return true;
    }
}
