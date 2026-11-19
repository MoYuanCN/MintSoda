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
     * 插件私有数据目录，已创建。
     * <p>
     * 目录在插件升级与重装时保留；卸载时随插件目录一起删除。
     * 需要 {@code DATA_READ} 或 {@code DATA_WRITE} 之一。直接使用该目录时请注意自行做路径校验，
     * 更推荐使用下面的按路径读写方法，它们会把路径限制在数据目录内。
     */
    File dataDir();

    /**
     * 列出数据目录下的文件，返回相对路径。
     *
     * @param dir 子目录，留空表示根目录
     * @return 相对路径列表
     */
    List<String> listDataFiles(String dir);

    /**
     * 读取数据目录下的文本文件。需要 {@code DATA_READ}。
     *
     * @param path 相对路径
     * @return 文件内容，不存在时返回空串
     */
    String readDataFile(String path);

    /**
     * 写入数据目录下的文本文件，自动创建父目录。需要 {@code DATA_WRITE}。
     *
     * @param path    相对路径
     * @param content 内容
     */
    void writeDataFile(String path, String content);

    /**
     * 删除数据目录下的文件。需要 {@code DATA_WRITE}。
     *
     * @param path 相对路径
     * @return 是否删除了文件
     */
    boolean deleteDataFile(String path);

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
     * 记录警告日志；默认按普通日志输出，便于旧宿主保持兼容
     */
    default void logWarn(String message) {
        log(message);
    }

    /**
     * 记录错误日志；默认按普通日志输出，便于旧宿主保持兼容
     */
    default void logError(String message) {
        log(message);
    }

    /**
     * 记录调试日志；默认按普通日志输出，便于旧宿主保持兼容
     */
    default void logDebug(String message) {
        log(message);
    }

    /**
     * 以当前启用状态查询能力
     */
    boolean enabled();

    /**
     * 用户是否授予了该权限；未授予时注册对应扩展点会被拒绝。
     */
    boolean hasPermission(PluginPermission permission);

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
     * 注册下载生命周期监听，可观察提交成功、完成与失败；不能修改任务。
     * <p>
     * 默认实现为空，保证只实现旧版接口的宿主不会因新增方法而编译失败。
     */
    default void registerDownloadListener(DownloadListener listener) {
    }

    /**
     * 注册插件自身的 HTTP 接口，路径挂载在 {@code /api/plugin/api/{pluginId}/} 下
     */
    void registerEndpoint(PluginEndpoint endpoint);

    List<NotificationChannel> notificationChannels();
}
