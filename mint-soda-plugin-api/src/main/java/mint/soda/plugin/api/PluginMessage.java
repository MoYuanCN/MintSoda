package mint.soda.plugin.api;

/**
 * 插件之间传递的消息。
 * <p>
 * 消息只包含纯文本载荷，宿主不解析其内容，格式由收发双方约定。
 * {@code source} 由宿主填入发送方插件标识，插件无法伪造。
 *
 * @param topic   消息主题，仅允许小写字母、数字、点、下划线与连字符
 * @param source  发送方插件标识
 * @param payload 载荷文本，最长 64KB
 */
public record PluginMessage(String topic, String source, String payload) {
}
