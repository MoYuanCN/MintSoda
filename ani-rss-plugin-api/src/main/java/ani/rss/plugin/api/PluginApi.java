package ani.rss.plugin.api;

/**
 * 插件接口版本信息。
 * <p>
 * 宿主只加载 {@link #VERSION} 不大于自身支持版本的插件；破坏性变更时必须提升版本号。
 */
public final class PluginApi {

    /**
     * 当前插件接口版本
     */
    public static final int VERSION = 2;

    /**
     * 插件描述文件名，安装时统一规范化为该名称
     */
    public static final String DESCRIPTOR_FILE = "plugin.json";

    /**
     * 可选的描述文件名，包内提供 manifest.json 时同样接受
     */
    public static final String MANIFEST_FILE = "manifest.json";

    private PluginApi() {
    }
}
