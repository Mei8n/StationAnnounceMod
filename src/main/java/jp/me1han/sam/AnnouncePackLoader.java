package jp.me1han.sam;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.me1han.sam.api.AnnounceData;
import jp.me1han.sam.api.AnnounceScriptInfo;
import jp.me1han.sam.render.TileEntityAnnouncer;
import net.minecraft.client.Minecraft;
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
        availableScripts.clear();
        scriptEngines.clear();

        File mcDir = Minecraft.getMinecraft().mcDataDir;
        File packDir = new File(mcDir, "mods" + File.separator + "SAMpacks");

        StationAnnounceModCore.logger.info("[SAM] Scanning directory: " + packDir.getAbsolutePath());

        if (!packDir.exists()) {
            packDir.mkdirs();
            return;
        }

        File[] files = packDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (files == null) return;

        for (File file : files) {
            StationAnnounceModCore.logger.info("[SAM] Loading External Pack: " + file.getName());
            StationAnnounceModCore.proxy.addResourcePack(file);

            try (ZipFile zip = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (name.endsWith("sam_length.json")) {
                        parseLengthJson(zip.getInputStream(entry));
                    }

                    if (name.contains("assets/stationannouncemod/scripts/") && name.endsWith(".js")) {
                        String scriptName = name.substring(name.lastIndexOf("/") + 1);
                        parseJavaScript(zip.getInputStream(entry), scriptName);
                    }
                }
            } catch (Exception e) {
                StationAnnounceModCore.logger.error("[SAM] Error parsing zip: " + file.getName(), e);
            }
        }
    }

    private static void parseLengthJson(InputStream is) {
        try {
            JsonObject json = new JsonParser().parse(new InputStreamReader(is, "UTF-8")).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                double seconds = entry.getValue().getAsJsonObject().get("length").getAsDouble();
                int ticks = (int) Math.ceil(seconds * 20);
                soundTicks.put(entry.getKey(), ticks);
            }
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] JSON error", e);
        }
    }

    private static void parseJavaScript(InputStream is, String scriptName) {
        try {
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
            engine.eval(new InputStreamReader(is, "UTF-8"));

            String displayName = scriptName;
            try {
                Invocable inv = (Invocable) engine;
                Object result = inv.invokeFunction("getDisplayName");
                if (result != null) displayName = result.toString();
            } catch (Exception e) {
                // getDisplayNameがない場合はファイル名を使用
            }

            scriptEngines.put(scriptName, engine);
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
            Invocable inv = (Invocable) engine;
            return (AnnounceData) inv.invokeFunction("samMain", tile);
        } catch (Exception e) {
            StationAnnounceModCore.logger.error("[SAM] Runtime Error", e);
        }
        return null;
    }
}
