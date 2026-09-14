package jp.me1han.sam.switchmodel;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Text MQO polygon reader, independent of Minecraft/RTM. Vertex coordinates stay in MQO units. */
public final class MqoMesh {
    public static final class Material {
        public String name;
    }
    public static final class Triangle {
        public final double[][] vertices = new double[3][];
        public final double[][] uv = new double[3][2];
        public final double[] normal = new double[3];
        public final double[][] vertexNormals = new double[3][3];
        public int material;
    }
    public final List<Material> materials = new ArrayList<>();
    public final Map<String, List<Triangle>> parts = new LinkedHashMap<>();
    public final Map<String, Double> smoothingAngles = new LinkedHashMap<>();
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern FIELDS = Pattern.compile("([A-Za-z]+)\\(([^)]*)\\)");

    public static MqoMesh read(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        MqoMesh mesh = new MqoMesh();
        List<double[]> vertices = new ArrayList<>();
        List<Triangle> faces = null;
        String section = "";
        String line;
        int number = 0, triangleCount = 0, mirrorMode = 0, mirrorAxis = 1;
        String currentPart = null;
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
                    currentPart = name;
                    faces = new ArrayList<>();
                    if (mesh.parts.put(name, faces) != null) throw new IllegalArgumentException("Duplicate object: " + name);
                    vertices = new ArrayList<>();
                    mirrorMode = 0;
                    mirrorAxis = 1;
                    section = "";
                    continue;
                }
                if (line.startsWith("facet ")) {
                    if (currentPart == null) throw new IllegalArgumentException("Facet outside an object");
                    double angle = Double.parseDouble(line.substring("facet ".length()).trim());
                    if (!Double.isFinite(angle) || angle < 0 || angle > 180) throw new IllegalArgumentException("Invalid facet angle");
                    mesh.smoothingAngles.put(currentPart, angle);
                    continue;
                }
                if (line.startsWith("vertex ")) { section = "vertex"; continue; }
                if (line.startsWith("face ")) { section = "face"; continue; }
                if (line.startsWith("BVertex")) throw new IllegalArgumentException("Use text MQO, not binary vertices");
                if (line.startsWith("mirror ")) {
                    mirrorMode = Integer.parseInt(line.substring("mirror ".length()).trim());
                    if (mirrorMode < 0 || mirrorMode > 2) throw new IllegalArgumentException("Unsupported mirror mode");
                    continue;
                }
                if (line.startsWith("mirror_axis ")) {
                    mirrorAxis = Integer.parseInt(line.substring("mirror_axis ".length()).trim());
                    if (mirrorAxis != 1 && mirrorAxis != 2 && mirrorAxis != 4)
                        throw new IllegalArgumentException("Freeze multi-axis mirrors before exporting MQO");
                    continue;
                }
                if (line.matches("patch\\s+[1-9].*")) throw new IllegalArgumentException("Freeze subdivision before exporting MQO");
                if (section.equals("material")) {
                    Material material = new Material();
                    material.name = quoted(line);
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
                        if (mirrorMode != 0) {
                            faces.add(mirror(triangle, mirrorAxis));
                            if (++triangleCount > 200000) throw new IllegalArgumentException("Too many MQO triangles");
                        }
                    }
                }
            }
            if (!signature || mesh.parts.isEmpty() || triangleCount == 0) throw new IllegalArgumentException("No MQO polygon geometry");
            if (mesh.materials.isEmpty()) { Material material = new Material(); material.name = "default"; mesh.materials.add(material); }
            for (List<Triangle> part : mesh.parts.values()) for (Triangle face : part) {
                if (face.material < 0 || face.material >= mesh.materials.size()) throw new IllegalArgumentException("Invalid material index");
            }
            mesh.calculateVertexNormals();
            return mesh;
        } catch (RuntimeException e) { throw new IOException("MQO line " + number + ": " + e.getMessage(), e); }
    }

    /** Matches NGTLib: smooth only shared vertices whose face angle is within the MQO Object facet value. */
    private void calculateVertexNormals() {
        for (Map.Entry<String, List<Triangle>> part : parts.entrySet()) {
            Map<VertexKey, List<Triangle>> adjacent = new HashMap<>();
            for (Triangle face : part.getValue()) for (double[] vertex : face.vertices) {
                VertexKey key = new VertexKey(vertex);
                List<Triangle> faces = adjacent.get(key);
                if (faces == null) { faces = new ArrayList<>(); adjacent.put(key, faces); }
                if (!faces.contains(face)) faces.add(face);
            }
            double angleCos = Math.cos(Math.toRadians(smoothingAngles.containsKey(part.getKey())
                ? smoothingAngles.get(part.getKey()) : 0));
            for (Triangle face : part.getValue()) for (int i = 0; i < 3; i++) {
                double[] result = face.vertexNormals[i];
                for (Triangle other : adjacent.get(new VertexKey(face.vertices[i]))) {
                    if (dot(face.normal, other.normal) + 1.0E-9 >= angleCos) {
                        result[0] += other.normal[0]; result[1] += other.normal[1]; result[2] += other.normal[2];
                    }
                }
                normalize(result);
            }
        }
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static void normalize(double[] value) {
        double length = Math.sqrt(dot(value, value));
        if (length > 0) for (int i = 0; i < 3; i++) value[i] /= length;
    }

    private static final class VertexKey {
        final long x, y, z;
        VertexKey(double[] value) {
            x = bits(value[0]); y = bits(value[1]); z = bits(value[2]);
        }
        private static long bits(double value) { return Double.doubleToLongBits(value == 0 ? 0 : value); }
        @Override public int hashCode() {
            long hash = x * 31L * 31L + y * 31L + z;
            return (int) (hash ^ (hash >>> 32));
        }
        @Override public boolean equals(Object value) {
            if (!(value instanceof VertexKey)) return false;
            VertexKey other = (VertexKey) value;
            return x == other.x && y == other.y && z == other.z;
        }
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

    private static Triangle mirror(Triangle source, int axes) {
        Triangle result = new Triangle();
        result.material = source.material;
        boolean reverse = Integer.bitCount(axes) % 2 != 0;
        for (int i = 0; i < 3; i++) {
            int from = reverse && i > 0 ? 3 - i : i;
            result.vertices[i] = source.vertices[from].clone();
            if ((axes & 1) != 0) result.vertices[i][0] = -result.vertices[i][0];
            if ((axes & 2) != 0) result.vertices[i][1] = -result.vertices[i][1];
            if ((axes & 4) != 0) result.vertices[i][2] = -result.vertices[i][2];
            result.uv[i][0] = source.uv[from][0];
            result.uv[i][1] = source.uv[from][1];
        }
        normal(result);
        return result;
    }
}
