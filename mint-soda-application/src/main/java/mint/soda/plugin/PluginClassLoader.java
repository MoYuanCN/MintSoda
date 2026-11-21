package mint.soda.plugin;

import mint.soda.plugin.api.PluginApi;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * 插件类加载器。
 * <p>
 * 父加载器已持有插件接口模块，因此接口类不会从插件包中重复加载，
 * 保证宿主与插件看到的是同一份 {@code mint.soda.plugin.api} 类型。
 */
public class PluginClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    private final String pluginId;

    public PluginClassLoader(String pluginId, URL jarUrl, ClassLoader parent) {
        super(new URL[]{jarUrl}, parent);
        this.pluginId = pluginId;
    }

    public String pluginId() {
        return pluginId;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 插件接口与 JDK 类必须由宿主提供，避免类型不一致或插件替换核心类型。
        if (name.startsWith("mint.soda.plugin.api.") || name.startsWith("java.")) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException e) {
                    loaded = super.loadClass(name, resolve);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    public int apiVersion() {
        return PluginApi.VERSION;
    }
}
