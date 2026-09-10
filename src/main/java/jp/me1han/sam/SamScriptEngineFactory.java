package jp.me1han.sam;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ServiceConfigurationError;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/** Creates the JDK 8 Nashorn engine used by SAM announcement scripts and tests. */
public final class SamScriptEngineFactory {
    private static final String NASHORN_NAME = "nashorn";
    private static final String JDK8_FACTORY_CLASS =
        "jdk.nashorn.api.scripting.NashornScriptEngineFactory";

    private SamScriptEngineFactory() {}

    public static ScriptEngine createEngine() {
        ScriptEngine engine = createFromManager();
        return engine != null ? engine : createFromJdk8Factory();
    }

    public static ScriptEngine requireEngine() {
        ScriptEngine engine = createEngine();
        if (engine == null) throw new IllegalStateException(unavailableMessage());
        return engine;
    }

    public static String unavailableMessage() {
        return "Nashorn JavaScript engine is unavailable. Expected Java runtime: Java 8. "
            + "Actual Java runtime: " + System.getProperty("java.runtime.version", "unknown")
            + " (" + System.getProperty("java.vendor", "unknown") + ").";
    }

    private static ScriptEngine createFromManager() {
        try {
            return new ScriptEngineManager().getEngineByName(NASHORN_NAME);
        } catch (ServiceConfigurationError error) {
            return null;
        } catch (LinkageError error) {
            return null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static ScriptEngine createFromJdk8Factory() {
        try {
            Class<?> factoryClass = Class.forName(JDK8_FACTORY_CLASS);
            Object factory = factoryClass.newInstance();
            Method getEngine = factoryClass.getMethod("getScriptEngine");
            Object engine = getEngine.invoke(factory);
            return engine instanceof ScriptEngine ? (ScriptEngine)engine : null;
        } catch (ClassNotFoundException error) {
            return null;
        } catch (InstantiationException error) {
            return null;
        } catch (IllegalAccessException error) {
            return null;
        } catch (NoSuchMethodException error) {
            return null;
        } catch (InvocationTargetException error) {
            rethrowFatal(error.getCause());
            return null;
        } catch (SecurityException error) {
            return null;
        } catch (LinkageError error) {
            return null;
        }
    }

    private static void rethrowFatal(Throwable cause) {
        if (cause instanceof VirtualMachineError) throw (VirtualMachineError)cause;
        if (cause instanceof ThreadDeath) throw (ThreadDeath)cause;
    }
}
