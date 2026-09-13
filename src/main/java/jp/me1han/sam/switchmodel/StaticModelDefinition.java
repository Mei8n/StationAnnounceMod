package jp.me1han.sam.switchmodel;

import java.util.Map;

/** Common immutable-at-runtime metadata consumed by the shared MQO cache/renderer. */
public interface StaticModelDefinition {
    String getName();
    String getModelFile();
    double getScale();
    double[] getModelOffset();
    Map<String, String> getTextures();
    void validateParts(java.util.Set<String> parts);
    boolean visible(String part, boolean state);
    double[] partOffset(String part, boolean state);
}
