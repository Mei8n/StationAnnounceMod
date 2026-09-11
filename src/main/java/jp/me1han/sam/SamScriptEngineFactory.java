package jp.me1han.sam;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.script.ScriptEngine;

/** Creates the JDK 8 Nashorn engine used by SAM announcement scripts and tests. */
public final class SamScriptEngineFactory {
    private static final String JDK8_FACTORY_CLASS =
        "jdk.nashorn.api.scripting.NashornScriptEngineFactory";
    private static final String JDK8_CLASS_FILTER =
        "jdk.nashorn.api.scripting.ClassFilter";
    private static final String DISABLE_HOST_GLOBALS =
        "this.Java=undefined;this.Packages=undefined;this.JavaImporter=undefined;"
        + "this.java=undefined;this.javax=undefined;this.com=undefined;this.org=undefined;this.edu=undefined;"
        + "this.load=undefined;this.loadWithNewGlobal=undefined;this.exit=undefined;this.quit=undefined;";

    private SamScriptEngineFactory() {}

    public static ScriptEngine createEngine() {
        return createFromJdk8Factory();
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

    private static ScriptEngine createFromJdk8Factory() {
        try {
            Class<?> factoryClass = Class.forName(JDK8_FACTORY_CLASS);
            Class<?> filterClass = Class.forName(JDK8_CLASS_FILTER);
            Object factory = factoryClass.newInstance();
            Object denyAllClasses = Proxy.newProxyInstance(filterClass.getClassLoader(),
                new Class<?>[] {filterClass}, (proxy, method, args) -> {
                    if ("exposeToScripts".equals(method.getName())) return Boolean.FALSE;
                    if ("toString".equals(method.getName())) return "SAM deny-all ClassFilter";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                });
            Method getEngine = factoryClass.getMethod("getScriptEngine", filterClass);
            Object value = getEngine.invoke(factory, denyAllClasses);
            if (!(value instanceof ScriptEngine)) return null;
            ScriptEngine engine = (ScriptEngine)value;
            engine.eval(DISABLE_HOST_GLOBALS);
            return engine;
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
        } catch (javax.script.ScriptException error) {
            return null;
        } catch (RuntimeException error) {
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
