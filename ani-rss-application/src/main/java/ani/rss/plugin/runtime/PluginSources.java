package ani.rss.plugin.runtime;

import ani.rss.plugin.PluginManager;
import ani.rss.plugin.PluginPaths;
import ani.rss.plugin.api.PluginDescriptor;

import java.io.File;

/**
 * 按描述文件声明的运行时创建插件来源。
 * <p>
 * 这是唯一需要感知具体语言的地方；新增语言时在这里追加分支即可。
 */
public final class PluginSources {

    private PluginSources() {
    }

    /**
     * 为已安装插件创建来源。
     *
     * @param descriptor 描述信息
     */
    public static PluginSource create(PluginDescriptor descriptor) {
        if (descriptor.script()) {
            File dir = PluginPaths.dir(descriptor.id());
            return descriptor.lua()
                    ? new LuaPluginSource(descriptor, dir)
                    : new ScriptPluginSource(descriptor, dir);
        }
        File jar = PluginPaths.jarFile(descriptor.id());
        if (!jar.isFile()) {
            throw new IllegalArgumentException("插件文件不存在: " + descriptor.id());
        }
        return new JavaPluginSource(descriptor, jar, PluginManager.class.getClassLoader());
    }
}
