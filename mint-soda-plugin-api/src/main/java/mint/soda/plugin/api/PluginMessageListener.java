package mint.soda.plugin.api;

/**
 * 插件消息订阅者，接收其他插件发布的消息。
 * <p>
 * 回调在宿主线程中执行，注册需要 {@code PLUGIN_MESSAGE_RECEIVE} 权限。
 * 回调内再次发布消息会受转发层数限制，异常只记录日志，不影响其他订阅者。
 */
@FunctionalInterface
public interface PluginMessageListener {

    /**
     * 处理一条消息
     *
     * @param message 消息内容
     */
    void onMessage(PluginMessage message);
}
