package ani.rss.plugin.runtime;

import ani.rss.plugin.api.Plugin;
import ani.rss.plugin.api.PluginDescriptor;

import java.io.File;

/**
 * Lua 插件来源：入口脚本由 LuaJ 解释执行，不加载 luajava 库。
 */
public class LuaPluginSource implements PluginSource {

    private final PluginDescriptor descriptor;
    private final File baseDir;

    public LuaPluginSource(PluginDescriptor descriptor, File baseDir) {
        this.descriptor = descriptor;
        this.baseDir = baseDir;
    }

    @Override
    public Plugin load() {
        return new LuaPlugin(descriptor, baseDir);
    }
}
