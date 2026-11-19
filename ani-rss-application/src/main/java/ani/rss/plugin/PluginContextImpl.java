package ani.rss.plugin;

import ani.rss.plugin.api.*;
import lombok.extern.slf4j.Slf4j;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        if (!hasPermission(PluginPermission.DATA_READ) && !hasPermission(PluginPermission.DATA_WRITE)) {
            throw new IllegalStateException("插件未获得权限: " + PluginPermission.DATA_READ.id());
        }
        File dir = PluginPaths.dataDir(descriptor.id());
        PluginPaths.ensure(dir);
        return dir;
    }

    @Override
    public List<String> listDataFiles(String dir) {
        if (!hasPermission(PluginPermission.DATA_READ)) {
            throw new IllegalStateException("插件未获得权限: " + PluginPermission.DATA_READ.id());
        }
        File base = PluginPaths.dataDir(descriptor.id());
        File target = PluginPaths.dataFile(descriptor.id(), dir);
        if (!target.isDirectory()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        // 只返回相对路径，且不跟随链接，避免插件目录成为任意文件读取入口。
        java.nio.file.Path basePath = base.toPath().toAbsolutePath().normalize();
        java.nio.file.Path targetPath = target.toPath().toAbsolutePath().normalize();
        try (java.util.stream.Stream<java.nio.file.Path> stream = Files.walk(targetPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .map(path -> basePath.relativize(path).toString().replace(java.io.File.separatorChar, '/'))
                    .sorted()
                    .forEach(result::add);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取插件数据目录失败: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public String readDataFile(String path) {
        if (!hasPermission(PluginPermission.DATA_READ)) {
            throw new IllegalStateException("插件未获得权限: " + PluginPermission.DATA_READ.id());
        }
        File file = PluginPaths.dataFile(descriptor.id(), path);
        if (!file.isFile() || java.nio.file.Files.isSymbolicLink(file.toPath())) {
            return "";
        }
        return FileUtil.readUtf8String(file);
    }

    @Override
    public void writeDataFile(String path, String content) {
        if (!hasPermission(PluginPermission.DATA_WRITE)) {
            throw new IllegalStateException("插件未获得权限: " + PluginPermission.DATA_WRITE.id());
        }
        File file = PluginPaths.dataFile(descriptor.id(), path);
        if (file.isDirectory() || java.nio.file.Files.isSymbolicLink(file.toPath())) {
            throw new IllegalArgumentException("非法的数据路径");
        }
        FileUtil.mkParentDirs(file);
        FileUtil.writeUtf8String(StrUtil.nullToEmpty(content), file);
    }

    @Override
    public boolean deleteDataFile(String path) {
        if (!hasPermission(PluginPermission.DATA_WRITE)) {
            throw new IllegalStateException("插件未获得权限: " + PluginPermission.DATA_WRITE.id());
        }
        File file = PluginPaths.dataFile(descriptor.id(), path);
        if (StrUtil.isBlank(path) || java.nio.file.Files.isSymbolicLink(file.toPath())) {
            return false;
        }
        return FileUtil.del(file);
    }

    @Override
    public String appVersion() {
        return appVersion;
    }

    @Override
    public String getConfig() {
        if (!hasPermission(PluginPermission.CONFIG_READ)) {
            // 返回空串而不是抛错，避免插件在加载阶段直接失败。
            return "";
        }
        return registry.get(descriptor.id())
                .map(PluginRecord::getConfig)
                .orElse("");
    }

    @Override
    public synchronized void saveConfig(String json) {
        if (!require(PluginPermission.CONFIG_WRITE)) {
            throw new IllegalStateException("插件未获得权限: " + PluginPermission.CONFIG_WRITE.id());
        }
        PluginRecord record = registry.getOrCreate(descriptor.id());
        record.setConfig(json);
        registry.sync();
    }

    @Override
    public void log(String message) {
        if (!hasPermission(PluginPermission.LOG)) {
            return;
        }
        log.info("[plugin:{}] {}", descriptor.id(), message);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean hasPermission(PluginPermission permission) {
        return permission != null && granted().contains(permission);
    }

    /**
     * 当前授予的权限。
     * <p>
     * 记录为 {@code null} 表示尚未单独配置（旧版本安装的插件），按描述文件声明处理；
     * 空列表表示用户主动关闭了全部权限，不能再回落到声明值。
     */
    private Set<PluginPermission> granted() {
        List<String> ids = registry.get(descriptor.id())
                .map(PluginRecord::getGrantedPermissions)
                .orElse(null);
        if (ids == null) {
            ids = descriptor.permissions() == null ? List.of() : descriptor.permissions();
        }
        Set<PluginPermission> result = new LinkedHashSet<>();
        for (String id : PluginPermission.sanitize(ids)) {
            PluginPermission.of(id).ifPresent(result::add);
        }
        return result;
    }

    /**
     * 注册扩展点前的权限校验。
     * <p>
     * 未授予时只跳过该扩展点，不让整个插件启用失败——用户可能只想关掉某个能力。
     *
     * @param permission 需要的权限
     * @return 是否允许继续注册
     */
    private boolean require(PluginPermission permission) {
        if (hasPermission(permission)) {
            return true;
        }
        log.warn("插件 {} 缺少权限 {}，已跳过对应注册", descriptor.id(), permission.id());
        return false;
    }

    void markEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void registerNotificationChannel(NotificationChannel channel) {
        if (!require(PluginPermission.NOTIFICATION)) {
            return;
        }
        channels.add(channel);
    }

    @Override
    public void registerItemFilter(ItemFilter filter) {
        if (!require(PluginPermission.ITEM_FILTER)) {
            return;
        }
        filters.add(filter);
    }

    @Override
    public void registerSubscriptionListener(SubscriptionListener listener) {
        if (!require(PluginPermission.SUBSCRIPTION_EVENTS)) {
            return;
        }
        listeners.add(listener);
    }

    @Override
    public void registerScheduledTask(ScheduledPluginTask task) {
        if (!require(PluginPermission.SCHEDULED_TASK)) {
            return;
        }
        tasks.add(task);
    }

    @Override
    public void registerSettingsSection(SettingsSection section) {
        sections.add(section);
    }

    @Override
    public void registerTemplateVariableProvider(TemplateVariableProvider provider) {
        if (!require(PluginPermission.TEMPLATE_VARIABLE)) {
            return;
        }
        variableProviders.add(provider);
    }

    @Override
    public void registerDownloadInterceptor(DownloadInterceptor interceptor) {
        if (!require(PluginPermission.DOWNLOAD_INTERCEPT)) {
            return;
        }
        downloadInterceptors.add(interceptor);
    }

    @Override
    public void registerEndpoint(PluginEndpoint endpoint) {
        if (!require(PluginPermission.HTTP_ENDPOINT)) {
            return;
        }
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
