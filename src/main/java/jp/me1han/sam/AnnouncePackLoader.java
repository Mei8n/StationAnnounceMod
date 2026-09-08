package jp.me1han.sam;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.me1han.sam.api.AnnounceData;
import jp.me1han.sam.api.AnnounceScriptInfo;
import jp.me1han.sam.render.TileEntityAnnouncer;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class AnnouncePackLoader {
    public static final Map<String, Integer> soundTicks = new ConcurrentHashMap<>();
    public static final Map<String, ScriptEngine> scriptEngines = new ConcurrentHashMap<>();
    public static final List<AnnounceScriptInfo> availableScripts = new ArrayList<>();

    public static void loadPacks() {
        jp.me1han.sam.switchmodel.SwitchModelRegistry.reset();
        availableScripts.clear();
        scriptEngines.clear();
        soundTicks.clear();
        parseLengthJson(AnnouncePackLoader.class.getResourceAsStream("/assets/stationannouncemod/sam_length.json"));

        File packDir = StationAnnounceModCore.samPacksDir;

        StationAnnounceModCore.logger.info("[SAM] Scanning directory: " + packDir.getAbsolutePath());

        if (!packDir.exists()) {
            packDir.mkdirs();
            return;
        }

        File[] files = packDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (files == null) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));

        for (File file : files) {
            StationAnnounceModCore.logger.info("[SAM] Loading External Pack: " + file.getName());
            StationAnnounceModCore.proxy.addResourcePack(file);

            try (ZipFile zip = new ZipFile(file)) {
                jp.me1han.sam.switchmodel.SwitchModelRegistry.loadPack(zip);
                loadScripts(zip);
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (name.endsWith("sam_length.json")) {
                        parseLengthJson(zip.getInputStream(entry));
                    }

                }
            } catch (Exception e) {
                StationAnnounceModCore.logger.error("[SAM] Error parsing zip: " + file.getName(), e);
            }
        }
    }

    static void loadScripts(ZipFile zip) throws java.io.IOException {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String path = entry.getName();
            if (!entry.isDirectory() && path.startsWith("assets/stationannouncemod/scripts/")
                && path.endsWith(".js")) {
                parseJavaScript(zip.getInputStream(entry), path.substring(path.lastIndexOf('/') + 1));
            }
        }
    }

    static void parseLengthJson(InputStream is) {
        try (InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                try {
                    double seconds = entry.getValue().getAsJsonObject().get("length").getAsDouble();
                    if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds <= 0 || seconds > 3600)
                        throw new IllegalArgumentException("Duration must be >0 to 3600 seconds");
                    soundTicks.put(entry.getKey(), (int) Math.ceil(seconds * 20));
                } catch (Exception e) {
                    // Invalidate an earlier pack's value as well; never reuse it for a broken override.
                    soundTicks.put(entry.getKey(), 0);
                    StationAnnounceModCore.logger.error("[SAM] Invalid sam_length.json duration: " + entry.getKey(), e);
                }
            }
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] JSON error", e);
        }
    }

    private static void parseJavaScript(InputStream is, String scriptName) {
        try (InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
            ScriptEngine engine = null;

            // 強力なリフレクションによるNashorn直接取得 (KaizPatchX的なアプローチの強化版)
            try {
                Class<?> factoryClass = Class.forName("jdk.nashorn.api.scripting.NashornScriptEngineFactory");
                Object factory = factoryClass.newInstance();
                Method getEngine = factoryClass.getMethod("getScriptEngine");
                engine = (ScriptEngine) getEngine.invoke(factory);
            } catch (Throwable t) {
                StationAnnounceModCore.logger.warn("[SAM] Direct factory access failed, trying Manager...");
            }

            // マネージャー経由のフォールバック
            if (engine == null) {
                ScriptEngineManager manager = new ScriptEngineManager(null);
                engine = manager.getEngineByName("nashorn");
            }

            if (engine == null) {
                StationAnnounceModCore.logger.error("[SAM] CRITICAL: Nashorn is not available in this JVM.");
                return;
            }

            engine.put("sam", new SAMScriptAPI());
            engine.eval(reader);

            String displayName = scriptName;
            try {
                Invocable inv = (Invocable) engine;
                Object result = inv.invokeFunction("getDisplayName");
                if (result != null) displayName = result.toString();
            } catch (Exception e) {
                // getDisplayNameがない場合はファイル名を使用
            }

            scriptEngines.put(scriptName, engine);
            availableScripts.removeIf(info -> info.fileName.equals(scriptName));
            availableScripts.add(new AnnounceScriptInfo(scriptName, displayName));
            StationAnnounceModCore.logger.info("[SAM] Registered: " + displayName);

        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] JS Error in " + scriptName, e);
        }
    }

    public static AnnounceData runScript(String name, TileEntityAnnouncer tile) {
        try {
            ScriptEngine engine = scriptEngines.get(name);
            if (engine == null) return null;
            synchronized (engine) {
                Invocable inv = (Invocable) engine;
                return (AnnounceData) inv.invokeFunction("samMain", tile);
            }
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Runtime Error", e);
        }
        return null;
    }

    public static jp.me1han.sam.api.DepartureProgram runDepartureScript(String name,
            jp.me1han.sam.render.TileEntityDepartureMelody tile) throws Exception {
        ScriptEngine engine = scriptEngines.get(name);
        if (engine == null) throw new IllegalArgumentException("Departure script not found: " + name);
        synchronized (engine) {
            Object value = ((Invocable) engine).invokeFunction("samMain", tile);
            if (!(value instanceof jp.me1han.sam.api.DepartureProgram)) {
                throw new IllegalArgumentException("Departure samMain must return sam.build(melody, sounds, mode)");
            }
            return ((jp.me1han.sam.api.DepartureProgram) value).resolve(soundTicks);
        }
    }
}
