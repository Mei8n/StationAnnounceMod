package jp.me1han.sam.switchmodel;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Text MQO polygon reader, independent of Minecraft/RTM. Vertex coordinates stay in MQO units. */
public final class MqoMesh {
    public static final class Material {
        public String name;
        public String texture = "";
        public double[] color = {1, 1, 1, 1};
    }
    public static final class Triangle {
        public final double[][] vertices = new double[3][];
        public final double[][] uv = new double[3][2];
        public final double[] normal = new double[3];
        public int material;
    }
    public final List<Material> materials = new ArrayList<>();
    public final Map<String, List<Triangle>> parts = new LinkedHashMap<>();
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern FIELDS = Pattern.compile("([A-Za-z]+)\\(([^)]*)\\)");

    public static MqoMesh read(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        MqoMesh mesh = new MqoMesh();
        List<double[]> vertices = new ArrayList<>();
        List<Triangle> faces = null;
        String section = "";
        String line;
        int number = 0, triangleCount = 0;
        boolean signature = false;
        try {
            while ((line = reader.readLine()) != null) {
                number++;
                line = line.trim();
                if (line.startsWith("\uFEFF")) line = line.substring(1);
                if (line.isEmpty()) continue;
                if (line.equals("Metasequoia Document")) { signature = true; continue; }
                if (line.startsWith("}")) { section = ""; continue; }
                if (line.startsWith("Material ")) { section = "material"; continue; }
                if (line.startsWith("Object ")) {
                    String name = quoted(line);
                    faces = new ArrayList<>();
                    if (mesh.parts.put(name, faces) != null) throw new IllegalArgumentException("Duplicate object: " + name);
                    vertices = new ArrayList<>();
                    section = "";
                    continue;
                }
                if (line.startsWith("vertex ")) { section = "vertex"; continue; }
                if (line.startsWith("face ")) { section = "face"; continue; }
                if (line.startsWith("BVertex")) throw new IllegalArgumentException("Use text MQO, not binary vertices");
                if (line.matches("(mirror|patch)\\s+[1-9].*")) throw new IllegalArgumentException("Freeze mirrors/subdivision before exporting MQO");
                if (section.equals("material")) {
                    Material material = new Material();
                    material.name = quoted(line);
                    Map<String, String> fields = fields(line);
                    if (fields.containsKey("col")) material.color = numbers(fields.get("col"), 4);
                    if (fields.containsKey("tex")) material.texture = quoted(fields.get("tex")).replace('\\', '/');
                    mesh.materials.add(material);
                } else if (section.equals("vertex")) {
                    if (vertices.size() >= 200000) throw new IllegalArgumentException("Too many MQO vertices");
                    vertices.add(numbers(line, 3));
                } else if (section.equals("face")) {
                    if (faces == null) throw new IllegalArgumentException("Face outside an object");
                    int count = Integer.parseInt(line.split("\\s+", 2)[0]);
                    if (count < 3) continue;
                    if (count > 4) throw new IllegalArgumentException("Triangulate polygons with more than four vertices");
                    Map<String, String> fields = fields(line);
                    double[] indices = numbers(fields.get("V"), count);
                    double[] uvs = fields.containsKey("UV") ? numbers(fields.get("UV"), count * 2) : new double[count * 2];
                    int material = fields.containsKey("M") ? Integer.parseInt(fields.get("M")) : 0;
                    for (int i = 1; i < count - 1; i++) {
                        Triangle triangle = new Triangle();
                        triangle.material = material;
                        // MQO uses clockwise winding; Minecraft/OpenGL use counter-clockwise.
                        int[] corners = {0, count - i, count - i - 1};
                        for (int c = 0; c < 3; c++) {
                            int corner = corners[c];
                            int index = (int) indices[corner];
                            if (index != indices[corner] || index < 0 || index >= vertices.size()) throw new IllegalArgumentException("Invalid face vertex index");
                            triangle.vertices[c] = vertices.get(index);
                            triangle.uv[c][0] = uvs[corner * 2];
                            triangle.uv[c][1] = uvs[corner * 2 + 1];
                        }
                        normal(triangle);
                        faces.add(triangle);
                        if (++triangleCount > 200000) throw new IllegalArgumentException("Too many MQO triangles");
                    }
                }
            }
            if (!signature || mesh.parts.isEmpty() || triangleCount == 0) throw new IllegalArgumentException("No MQO polygon geometry");
            if (mesh.materials.isEmpty()) { Material material = new Material(); material.name = "default"; mesh.materials.add(material); }
            for (List<Triangle> part : mesh.parts.values()) for (Triangle face : part) {
                if (face.material < 0 || face.material >= mesh.materials.size()) throw new IllegalArgumentException("Invalid material index");
            }
            return mesh;
        } catch (RuntimeException e) { throw new IOException("MQO line " + number + ": " + e.getMessage(), e); }
    }

    private static Map<String, String> fields(String line) {
        Map<String, String> result = new HashMap<>();
        Matcher matcher = FIELDS.matcher(line);
        while (matcher.find()) result.put(matcher.group(1), matcher.group(2));
        return result;
    }
    private static String quoted(String value) {
        Matcher matcher = QUOTED.matcher(value);
        if (!matcher.find()) throw new IllegalArgumentException("Expected quoted name");
        return matcher.group(1);
    }
    private static double[] numbers(String value, int count) {
        if (value == null) throw new IllegalArgumentException("Missing numeric data");
        String[] tokens = value.trim().split("\\s+");
        if (tokens.length != count) throw new IllegalArgumentException("Expected " + count + " numbers");
        double[] result = new double[count];
        for (int i = 0; i < count; i++) {
            result[i] = Double.parseDouble(tokens[i]);
            if (!Double.isFinite(result[i]) || Math.abs(result[i]) > 1000000) throw new IllegalArgumentException("Invalid MQO number");
        }
        return result;
    }
    private static void normal(Triangle triangle) {
        double[] a = triangle.vertices[0], b = triangle.vertices[1], c = triangle.vertices[2];
        double x1 = b[0] - a[0], y1 = b[1] - a[1], z1 = b[2] - a[2];
        double x2 = c[0] - a[0], y2 = c[1] - a[1], z2 = c[2] - a[2];
        double[] n = triangle.normal;
        n[0] = y1 * z2 - z1 * y2; n[1] = z1 * x2 - x1 * z2; n[2] = x1 * y2 - y1 * x2;
        double length = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (length > 0) for (int i = 0; i < 3; i++) n[i] /= length;
    }
}
