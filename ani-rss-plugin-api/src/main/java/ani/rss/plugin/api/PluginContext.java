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

    /**
     * 注册模板变量提供方，可为通知、重命名与下载路径模板追加占位符
     */
    void registerTemplateVariableProvider(TemplateVariableProvider provider);

    /**
     * 注册下载拦截器，可在种子提交给下载器之前调整或取消任务
     */
    void registerDownloadInterceptor(DownloadInterceptor interceptor);

    /**
     * 注册插件自身的 HTTP 接口，路径挂载在 {@code /api/plugin/api/{pluginId}/} 下
     */
    void registerEndpoint(PluginEndpoint endpoint);

    List<NotificationChannel> notificationChannels();
}
