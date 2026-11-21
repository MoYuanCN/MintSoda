package mint.soda.plugin.api;

/**
 * 请求式消息的应答。
 *
 * @param plugin  应答方插件标识
 * @param payload 应答文本
 */
public record PluginReply(String plugin, String payload) {
}
