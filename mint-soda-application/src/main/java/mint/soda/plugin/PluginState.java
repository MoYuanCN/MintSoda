package mint.soda.plugin;

/**
 * 插件运行状态
 */
public enum PluginState {
    /** 已安装但未启用 */
    DISABLED,
    /** 已启用 */
    ENABLED,
    /** 加载或运行失败 */
    ERROR,
    /** 接口版本或宿主版本不兼容 */
    INCOMPATIBLE
}
