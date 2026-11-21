package mint.soda.plugin.api;

/**
 * 其他插件的概要信息，供插件之间发现彼此。
 * <p>
 * 只包含描述文件中的公开字段与运行状态，不暴露路径、配置或权限细节。
 *
 * @param id      插件标识
 * @param name    显示名称
 * @param version 插件版本
 * @param runtime 运行时：JAVA / JAVASCRIPT / LUA
 * @param enabled 是否已启用
 */
public record PluginSummary(String id, String name, String version, String runtime, boolean enabled) {
}
