package ani.rss.plugin.api;

import java.util.List;
import java.util.Map;

/**
 * 插件提供的通知渠道。
 */
public interface NotificationChannel {

    /**
     * 渠道标识，写入通知配置的 notificationType
     */
    String id();

    /**
     * 显示名称
     */
    String name();

    /**
     * 渠道可配置字段，宿主据此渲染设置界面
     */
    default List<ConfigField> fields() {
        return List.of();
    }

    /**
     * 发送通知
     *
     * @param payload 通知内容
     * @param options 用户在通知配置中填写的字段
     */
    void send(NotificationPayload payload, Map<String, String> options) throws Exception;

    /**
     * 发送测试通知，默认复用 {@link #send}
     */
    default void test(NotificationPayload payload, Map<String, String> options) throws Exception {
        send(payload, options);
    }
}
