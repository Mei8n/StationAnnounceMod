package jp.me1han.sam;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PackLoader {
    // 音声IDとTick数を紐付けるキャッシュ (スレッドセーフ)
    public static final Map<String, Integer> soundTicks = new ConcurrentHashMap<>();

    public static void loadPacks() {
        File packDir = new File("mods/SAM/packs");
        if (!packDir.exists()) packDir.mkdirs();

        File[] files = packDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (files == null) return;

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (File file : files) {
            executor.submit(() -> {
                try (ZipFile zip = new ZipFile(file)) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();

                        // 1. sam_length.json の読み込み
                        if (name.endsWith("sam_length.json")) {
                            parseLengthJson(zip.getInputStream(entry));
                        }

                        // 2. JSファイルの読み込み
                        if (name.startsWith("assets/minecraft/scripts/") && name.endsWith(".js")) {
                            // JS名（ファイル名）をキーにしてキャッシュ
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
}
