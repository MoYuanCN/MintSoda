package mint.soda.plugin.api;

/**
 * 插件消息处理器，用于应答其他插件的请求式消息。
 * <p>
 * 返回值作为应答内容；返回 {@code null} 或空串表示不应答。
 * 注册需要 {@code PLUGIN_MESSAGE_RECEIVE} 权限，异常只记录日志，不影响其他处理器。
 */
@FunctionalInterface
public interface PluginMessageHandler {

    /**
     * 处理一条请求消息
     *
     * @param message 请求内容
     * @return 应答文本
     */
    String handle(PluginMessage message);
}
