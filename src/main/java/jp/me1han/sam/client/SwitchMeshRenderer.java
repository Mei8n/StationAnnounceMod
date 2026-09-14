package jp.me1han.sam.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import jp.me1han.sam.StationAnnounceModCore;
import jp.me1han.sam.switchmodel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** One shared mesh cache for world, inventory and GUI rendering. */
public final class SwitchMeshRenderer implements IResourceManagerReloadListener {
    public static final SwitchMeshRenderer INSTANCE = new SwitchMeshRenderer();
    private final Map<String, MqoMesh> meshes = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private final Set<String> failedTextures = new HashSet<>();
    private final Map<String, ResourceLocation> textureLocations = new HashMap<>();
    @Override public void onResourceManagerReload(IResourceManager manager) {
        meshes.clear(); failed.clear(); failedTextures.clear(); textureLocations.clear();
    }

    public MqoMesh mesh(StaticModelDefinition definition) {
        if (definition == null) return null;
        String cacheKey = definition.getClass().getName() + ":" + definition.getName();
        if (failed.contains(cacheKey)) return null;
        MqoMesh mesh = meshes.get(cacheKey);
        if (mesh == null) {
            try (Reader reader = new InputStreamReader(Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation(definition.getModelFile())).getInputStream(), StandardCharsets.UTF_8)) {
                mesh = MqoMesh.read(reader);
                definition.validateParts(mesh.parts.keySet());
                meshes.put(cacheKey, mesh);
            } catch (Exception e) {
                failed.add(cacheKey);
                StationAnnounceModCore.logger.error("[SAM] Cannot render model " + definition.getName(), e);
                return null;
            }
        }
        return mesh;
    }

    /** Coordinates are centered in X/Z; the model base is Y=0. Returns false for unavailable assets. */
    public boolean render(StaticModelDefinition definition, boolean state, int brightness) {
        MqoMesh mesh = mesh(definition);
        if (mesh == null) return false;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        try {
            if (definition.isCulling()) GL11.glEnable(GL11.GL_CULL_FACE);
            else GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glScaled(definition.getScale(), definition.getScale(), definition.getScale());
            if (definition.isSmoothing()) GL11.glShadeModel(GL11.GL_SMOOTH);
            double[] modelOffset = definition.getModelOffset();
            GL11.glTranslated(modelOffset[0], modelOffset[1], modelOffset[2]);
            for (Map.Entry<String, List<MqoMesh.Triangle>> part : mesh.parts.entrySet()) {
                if (!definition.visible(part.getKey(), state)) continue;
                double[] offset = definition.partOffset(part.getKey(), state);
                GL11.glPushMatrix();
                GL11.glTranslated(offset[0], offset[1], offset[2]);
                for (int m = 0; m < mesh.materials.size(); m++) {
                    MqoMesh.Material material = mesh.materials.get(m);
                    bindMaterialTexture(definition, material);
                    Tessellator tess = Tessellator.instance;
                    tess.startDrawing(GL11.GL_TRIANGLES);
                    tess.setBrightness(brightness);
                    for (MqoMesh.Triangle face : part.getValue()) {
                        if (face.material != m) continue;
                        tess.setColorRGBA_F(1, 1, 1, 1);
                        for (int i = 0; i < 3; i++) {
                            double[] normal = definition.isSmoothing() ? face.vertexNormals[i] : face.normal;
                            tess.setNormal((float) normal[0], (float) normal[1], (float) normal[2]);
                            tess.addVertexWithUV(face.vertices[i][0], face.vertices[i][1], face.vertices[i][2], face.uv[i][0], face.uv[i][1]);
                        }
                    }
                    tess.draw();
                }
                GL11.glPopMatrix();
            }
        } finally {
            if (definition.isSmoothing()) GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glPopMatrix(); GL11.glPopAttrib();
        }
        return true;
    }

    private void bindMaterialTexture(StaticModelDefinition definition, MqoMesh.Material material) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        String texture = ModelTextureResolver.select(definition.getTextures(), material.name);
        if (texture == null || failedTextures.contains(texture)) {
            bindMissingTexture();
            return;
        }
        try {
            // TextureManager caches successful resources and its IOException fallback.
            ResourceLocation location = textureLocations.get(texture);
            if (location == null) {
                location = new ResourceLocation(texture);
                textureLocations.put(texture, location);
            }
            Minecraft.getMinecraft().getTextureManager().bindTexture(location);
        } catch (RuntimeException error) {
            // Decode/runtime failures are isolated from geometry and retried after resource reload.
            failedTextures.add(texture);
            bindMissingTexture();
        }
    }

    private void bindMissingTexture() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, TextureUtil.missingTexture.getGlTextureId());
    }
}
