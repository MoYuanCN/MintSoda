package ani.rss.plugin.runtime;

import ani.rss.plugin.api.Plugin;
import ani.rss.plugin.api.PluginDescriptor;

import java.io.File;

/**
 * 脚本插件来源：入口文件由脚本引擎解释执行，不需要类加载器。
 */
public class ScriptPluginSource implements PluginSource {

    private final PluginDescriptor descriptor;
    private final File baseDir;

    public ScriptPluginSource(PluginDescriptor descriptor, File baseDir) {
        this.descriptor = descriptor;
        this.baseDir = baseDir;
    }

    @Override
    public Plugin load() {
        return new ScriptPlugin(descriptor, baseDir);
    }
}
