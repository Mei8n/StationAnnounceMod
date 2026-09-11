package jp.me1han.sam;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.me1han.sam.api.AnnounceData;
import jp.me1han.sam.api.AnnounceScriptInfo;
import jp.me1han.sam.api.DepartureProgram;
import jp.me1han.sam.network.PacketAnnounce;
import jp.me1han.sam.network.PacketDepartureStart;
import jp.me1han.sam.network.PacketLimits;
import jp.me1han.sam.render.TileEntityAnnouncer;
import jp.me1han.sam.script.AnnounceScriptContext;
import jp.me1han.sam.script.DepartureScriptContext;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.script.Invocable;
import javax.script.ScriptEngine;

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
        if (!PacketLimits.string(scriptName, PacketLimits.NAME)) {
            logScriptFailure(scriptName, "load", new IllegalArgumentException("Script filename is too long"));
            return;
        }
        ScriptEngine engine = SamScriptEngineFactory.createEngine();
        if (engine == null) {
            logScriptFailure(scriptName, "load", new IllegalStateException(SamScriptEngineFactory.unavailableMessage()));
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
            engine.put("sam", new SAMScriptAPI());
            engine.eval(reader);
            if (!Boolean.TRUE.equals(engine.eval("typeof samMain === 'function'")))
                throw new IllegalArgumentException("Required function samMain(tile) is missing");

        } catch (Throwable error) {
            rethrowFatal(error);
            logScriptFailure(scriptName, "load", error);
            return;
        }

        String displayName = scriptName;
        try {
            if (Boolean.TRUE.equals(engine.eval("typeof getDisplayName === 'function'"))) {
                Object result = ((Invocable) engine).invokeFunction("getDisplayName");
                if (result == null) throw new IllegalArgumentException("getDisplayName() returned null");
                String candidate = result.toString();
                if (!PacketLimits.string(candidate, PacketLimits.NAME))
                    throw new IllegalArgumentException("getDisplayName() exceeds " + PacketLimits.NAME + " characters");
                displayName = candidate;
            }
        } catch (Throwable error) {
            rethrowFatal(error);
            logScriptFailure(scriptName, "getDisplayName", error);
        }

        scriptEngines.put(scriptName, engine);
        availableScripts.removeIf(info -> info.fileName.equals(scriptName));
        availableScripts.add(new AnnounceScriptInfo(scriptName, displayName));
        StationAnnounceModCore.logger.info("[SAM] Registered: " + displayName);
    }

    public static AnnounceData runScript(String name, TileEntityAnnouncer tile) {
        try {
            ScriptEngine engine = scriptEngines.get(name);
            if (engine == null) throw new IllegalArgumentException("Script not found");
            AnnounceScriptContext context = AnnounceScriptContext.snapshot(tile);
            synchronized (engine) {
                Object value = ((Invocable) engine).invokeFunction("samMain", context);
                if (!(value instanceof AnnounceData))
                    throw new IllegalArgumentException("samMain(tile) must return sam.build(...) AnnounceData");
                AnnounceData data = (AnnounceData)value;
                PacketAnnounce validation = new PacketAnnounce(data, context.getLinkKey(), false, 0, 0, 0);
                validation.sessionId = 1;
                validation.resolveTiming(soundTicks);
                validation.validatePayload();
                return data;
            }
        } catch (Throwable error) {
            rethrowFatal(error);
            logScriptFailure(name, "samMain", error);
            return null;
        }
    }

    public static DepartureProgram runDepartureScript(String name,
            jp.me1han.sam.render.TileEntityDepartureMelody tile) throws Exception {
        try {
            ScriptEngine engine = scriptEngines.get(name);
            if (engine == null) throw new IllegalArgumentException("Departure script not found");
            DepartureScriptContext context = DepartureScriptContext.snapshot(tile);
            synchronized (engine) {
                Object value = ((Invocable) engine).invokeFunction("samMain", context);
                if (!(value instanceof DepartureProgram))
                    throw new IllegalArgumentException("Departure samMain(tile) must return sam.build(melody, sounds, mode)");
                DepartureProgram program = ((DepartureProgram)value).resolve(soundTicks);
                PacketDepartureStart validation = new PacketDepartureStart();
                validation.sessionId = 1; validation.linkKey = context.getLinkKey(); validation.departure = program;
                validation.validateDeparturePayload();
                return program;
            }
        } catch (Throwable error) {
            rethrowFatal(error);
            logScriptFailure(name, "samMain", error);
            if (error instanceof Exception) throw (Exception)error;
            throw new IllegalArgumentException(error.toString(), error);
        }
    }

    private static void logScriptFailure(String scriptName, String phase, Throwable error) {
        String cause = error.getMessage();
        if (cause == null || cause.isEmpty()) cause = error.getClass().getName();
        StationAnnounceModCore.logger.error("[SAM] Script '" + scriptName + "' failed during "
            + phase + ": " + cause, error);
    }

    private static void rethrowFatal(Throwable error) {
        if (error instanceof VirtualMachineError) throw (VirtualMachineError)error;
        if (error instanceof ThreadDeath) throw (ThreadDeath)error;
    }
}
