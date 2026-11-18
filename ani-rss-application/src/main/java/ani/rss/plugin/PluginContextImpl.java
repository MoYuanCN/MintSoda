package ani.rss.plugin;

import ani.rss.plugin.api.*;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 插件上下文实现。注册动作在插件启用期间生效，停用后由管理器统一清理。
 */
@Slf4j
public class PluginContextImpl implements PluginContext {

    private final PluginDescriptor descriptor;
    private final PluginRegistry registry;
    private final String appVersion;
    private final List<NotificationChannel> channels = new CopyOnWriteArrayList<>();
    private final List<ItemFilter> filters = new CopyOnWriteArrayList<>();
    private final List<SubscriptionListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ScheduledPluginTask> tasks = new CopyOnWriteArrayList<>();
    private final List<SettingsSection> sections = new CopyOnWriteArrayList<>();
    private final List<TemplateVariableProvider> variableProviders = new CopyOnWriteArrayList<>();
    private final List<DownloadInterceptor> downloadInterceptors = new CopyOnWriteArrayList<>();
    private final List<PluginEndpoint> endpoints = new CopyOnWriteArrayList<>();
    private volatile boolean enabled;

    public PluginContextImpl(PluginDescriptor descriptor, PluginRegistry registry, String appVersion) {
        this.descriptor = descriptor;
        this.registry = registry;
        this.appVersion = appVersion;
    }

    @Override
    public PluginDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public File dataDir() {
        File dir = PluginPaths.dataDir(descriptor.id());
        PluginPaths.ensure(dir);
        return dir;
    }

    @Override
    public String appVersion() {
        return appVersion;
    }

    @Override
    public String getConfig() {
        return registry.get(descriptor.id())
                .map(PluginRecord::getConfig)
                .orElse("");
    }

    @Override
    public synchronized void saveConfig(String json) {
        PluginRecord record = registry.getOrCreate(descriptor.id());
        record.setConfig(json);
        registry.sync();
    }

    @Override
    public void log(String message) {
        log.info("[plugin:{}] {}", descriptor.id(), message);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    void markEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void registerNotificationChannel(NotificationChannel channel) {
        channels.add(channel);
    }

    @Override
    public void registerItemFilter(ItemFilter filter) {
        filters.add(filter);
    }

    @Override
    public void registerSubscriptionListener(SubscriptionListener listener) {
        listeners.add(listener);
    }

    @Override
    public void registerScheduledTask(ScheduledPluginTask task) {
        tasks.add(task);
    }

    @Override
    public void registerSettingsSection(SettingsSection section) {
        sections.add(section);
    }

    @Override
    public void registerTemplateVariableProvider(TemplateVariableProvider provider) {
        variableProviders.add(provider);
    }

    @Override
    public void registerDownloadInterceptor(DownloadInterceptor interceptor) {
        downloadInterceptors.add(interceptor);
    }

    @Override
    public void registerEndpoint(PluginEndpoint endpoint) {
        endpoints.add(endpoint);
    }

    @Override
    public List<NotificationChannel> notificationChannels() {
        return List.copyOf(channels);
    }

    public List<ItemFilter> filters() {
        return List.copyOf(filters);
    }

    public List<SubscriptionListener> listeners() {
        return List.copyOf(listeners);
    }

    public List<ScheduledPluginTask> tasks() {
        return List.copyOf(tasks);
    }

    public List<SettingsSection> sections() {
        return List.copyOf(sections);
    }

    public List<TemplateVariableProvider> variableProviders() {
        return List.copyOf(variableProviders);
    }

    public List<DownloadInterceptor> downloadInterceptors() {
        return List.copyOf(downloadInterceptors);
    }

    public List<PluginEndpoint> endpoints() {
        return List.copyOf(endpoints);
    }

    /**
     * 停用时清空注册表；调度线程由管理器负责停止。
     */
    public void clearRegistrations() {
        channels.clear();
        filters.clear();
        listeners.clear();
        tasks.clear();
        sections.clear();
        variableProviders.clear();
        downloadInterceptors.clear();
        endpoints.clear();
    }
}
