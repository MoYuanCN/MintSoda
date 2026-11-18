package ani.rss.plugin.api;

import java.io.File;
import java.util.List;

/**
 * 宿主提供给插件的上下文。
 * <p>
 * 只暴露稳定的、与内部实现解耦的能力；插件不得依赖宿主内部类。
 */
public interface PluginContext {

    PluginDescriptor descriptor();

    /**
     * 插件私有数据目录，已创建；插件卸载时可保留或删除。
     */
    File dataDir();

    /**
     * 宿主张版本
     */
    String appVersion();

    /**
     * 读写插件自身配置（JSON 字符串），由宿主保存到插件数据目录。
     */
    String getConfig();

    void saveConfig(String json);

    /**
     * 记录日志，会带上插件标识
     */
    void log(String message);

    /**
     * 以当前启用状态查询能力
     */
    boolean enabled();

    /**
     * 注册通知渠道
     */
    void registerNotificationChannel(NotificationChannel channel);

    /**
     * 注册订阅过滤器
     */
    void registerItemFilter(ItemFilter filter);

    /**
     * 注册订阅生命周期监听
     */
    void registerSubscriptionListener(SubscriptionListener listener);

    /**
     * 注册后台周期任务
     */
    void registerScheduledTask(ScheduledPluginTask task);

    /**
     * 注册设置页展示项
     */
    void registerSettingsSection(SettingsSection section);

    List<NotificationChannel> notificationChannels();
}
