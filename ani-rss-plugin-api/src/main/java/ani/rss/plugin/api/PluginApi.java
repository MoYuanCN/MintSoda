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
    public static final int VERSION = 1;

    /**
     * 插件描述文件名
     */
    public static final String DESCRIPTOR_FILE = "plugin.json";

    private PluginApi() {
    }
}
