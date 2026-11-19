package ani.rss.plugin.runtime;

import ani.rss.plugin.PluginClassLoader;
import ani.rss.plugin.api.Plugin;
import ani.rss.plugin.api.PluginDescriptor;

import java.io.File;
import java.net.URL;

/**
 * Java 插件来源：每个插件一个类加载器，入口类实现 {@link Plugin}。
 */
public class JavaPluginSource implements PluginSource {

    private final PluginDescriptor descriptor;
    private final PluginClassLoader classLoader;
    private final URL jarUrl;

    public JavaPluginSource(PluginDescriptor descriptor, File jar, ClassLoader parent) {
        this.descriptor = descriptor;
        try {
            this.jarUrl = jar.toURI().toURL();
            this.classLoader = new PluginClassLoader(descriptor.id(), jarUrl, parent);
        } catch (Exception e) {
            throw new IllegalStateException("插件加载失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Plugin load() {
        try {
            Class<?> mainClass = classLoader.loadClass(descriptor.mainClass());
            if (!Plugin.class.isAssignableFrom(mainClass)) {
                throw new IllegalArgumentException("插件入口未实现 Plugin 接口: " + descriptor.mainClass());
            }
            return (Plugin) mainClass.getDeclaredConstructor().newInstance();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("插件加载失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            classLoader.close();
        } catch (Exception ignored) {
        }
    }

    public URL jarUrl() {
        return jarUrl;
    }
}
