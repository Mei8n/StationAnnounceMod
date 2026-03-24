package jp.me1han.sam;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStream; // 追加
import java.io.InputStreamReader;
import java.util.Enumeration; // 追加（重要）
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.List;
import java.util.ArrayList;

public class PackLoader {
    public static final Map<String, Integer> soundTicks = new ConcurrentHashMap<>();

    public static void loadPacks() {
        File packDir = new File("mods/SAM/packs");
        if (!packDir.exists()) packDir.mkdirs();

        File[] files = packDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (files == null) return;

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (File file : files) {
            // クライアント側でリソースパックとして登録
            StationAnnounceMod.proxy.addResourcePack(file);

            executor.submit(() -> {
                try (ZipFile zip = new ZipFile(file)) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();

                        if (name.endsWith("sam_length.json")) {
                            parseLengthJson(zip.getInputStream(entry));
                        }

                        if (name.startsWith("assets/minecraft/scripts/") && name.endsWith(".js")) {
                            String scriptName = name.substring(name.lastIndexOf("/") + 1);
                            parseJavaScript(zip.getInputStream(entry), scriptName);
                        }
                    }
                } catch (Exception e) {
                    StationAnnounceMod.logger.error("Error loading zip: " + file.getName(), e);
                }
            });
        }
        executor.shutdown();
    }

    private static void parseLengthJson(InputStream is) {
        try {
            JsonObject json = new JsonParser().parse(new InputStreamReader(is)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                // length (秒) を取得して Tick に変換
                double seconds = entry.getValue().getAsJsonObject().get("length").getAsDouble();
                int ticks = (int) Math.ceil(seconds * 20);
                soundTicks.put(entry.getKey(), ticks);
            }
        } catch (Exception e) {
            StationAnnounceMod.logger.error("Failed to parse sam_length.json", e);
        }
    }

    private static void parseJavaScript(InputStream is, String scriptName) {
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            // JSファイルを読み込み実行
            engine.eval(new InputStreamReader(is));

            // JS内の getSequence() 関数を呼び出す
            Invocable inv = (Invocable) engine;
            Object result = inv.invokeFunction("getSequence");

            if (result instanceof List) {
                // JSから返ってきたリストをJavaのリストとして受け取る
                // ここで AnnounceRegistry.scriptCache などに保存します
                StationAnnounceMod.logger.info("Successfully loaded script: " + scriptName);
            }
        } catch (Exception e) {
            StationAnnounceMod.logger.error("Failed to parse script: " + scriptName, e);
        }
    }
}
