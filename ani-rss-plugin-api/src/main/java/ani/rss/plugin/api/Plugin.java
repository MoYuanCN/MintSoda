package ani.rss.plugin.api;

/**
 * 插件入口。实现类必须提供无参构造函数，且不应在构造函数中访问宿主能力。
 */
public interface Plugin {

    /**
     * 插件被宿主识别后调用一次，此时插件可能尚未启用。
     *
     * @param context 宿主上下文
     */
    default void onLoad(PluginContext context) {
    }

    /**
     * 插件启用时调用；扩展点应在此注册。
     *
     * @param context 宿主上下文
     */
    default void onEnable(PluginContext context) {
    }

    /**
     * 插件停用或宿主关闭时调用，用于释放线程与连接。
     *
     * @param context 宿主上下文
     */
    default void onDisable(PluginContext context) {
    }

    /**
     * 插件被卸载前调用。
     *
     * @param context 宿主上下文
     */
    default void onUnload(PluginContext context) {
    }
}
