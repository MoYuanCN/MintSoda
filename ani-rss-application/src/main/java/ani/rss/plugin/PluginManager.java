package ani.rss.plugin;

import ani.rss.commons.GsonStatic;
import ani.rss.commons.MavenUtils;
import ani.rss.plugin.api.*;
import ani.rss.plugin.runtime.PluginSource;
import ani.rss.plugin.runtime.PluginSources;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 插件加载、启停与扩展点分发。
 * <p>
 * 插件包为单个 JAR，内部包含 plugin.json 与入口类；加载失败只影响该插件。
 */
@Slf4j
@Component
public class PluginManager {

    private final Map<String, LoadedPlugin> loaded = new ConcurrentHashMap<>();
    /**
     * 每个插件已注册的调度任务，停用时必须取消，否则重复启用会叠加执行。
     */
    private final Map<String, List<ScheduledFuture<?>>> scheduledTasks = new ConcurrentHashMap<>();
    private final PluginRegistry registry = PluginRegistry.load();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "plugin-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    public record LoadedPlugin(PluginDescriptor descriptor, Plugin plugin, PluginContextImpl context,
                               ani.rss.plugin.runtime.PluginSource source) {
    }

    /**
     * 启动时加载全部已安装插件，并启用标记为启用的插件。
     */
    // ---- 生命周期：加载 / 启用 / 停用 / 重载 / 卸载 ----

    public synchronized void init() {
        PluginPaths.ensure(PluginPaths.root());
        for (File dir : Objects.requireNonNullElse(PluginPaths.root().listFiles(File::isDirectory), new File[0])) {
            String id = dir.getName();
            if (!id.matches(PluginPaths.ID_REG)) {
                continue;
            }
            try {
                load(id);
            } catch (Exception e) {
                log.error("插件 {} 加载失败", id);
                log.error(e.getMessage(), e);
            }
        }
        for (String id : new ArrayList<>(loaded.keySet())) {
            boolean enable = registry.get(id).map(PluginRecord::getEnable).orElse(false);
            if (enable) {
                try {
                    enable(id);
                } catch (Exception e) {
                    log.error("插件 {} 启用失败", id);
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 重新扫描插件目录。
     * <p>
     * 导入备份或手工放入插件后使用：先卸载内存中已不存在的插件，再加载磁盘上新增的插件，
     * 已存在的插件保持原状态，避免重复执行生命周期。
     */
    public synchronized void rescan() {
        PluginPaths.ensure(PluginPaths.root());
        java.util.Set<String> onDisk = new java.util.HashSet<>();
        for (File dir : Objects.requireNonNullElse(PluginPaths.root().listFiles(File::isDirectory), new File[0])) {
            if (dir.getName().matches(PluginPaths.ID_REG)) {
                onDisk.add(dir.getName());
            }
        }
        for (String id : new ArrayList<>(loaded.keySet())) {
            if (!onDisk.contains(id)) {
                cancelTasks(id);
                disable(id);
                LoadedPlugin removed = loaded.remove(id);
                if (removed != null) {
                    try {
                        removed.plugin().onUnload(removed.context());
                        removed.source().close();
                    } catch (Exception e) {
                        log.warn("插件 {} 移除时资源释放失败", id);
                    }
                }
            }
        }
        for (String id : onDisk) {
            if (loaded.containsKey(id)) {
                continue;
            }
            try {
                load(id);
                if (registry.get(id).map(PluginRecord::getEnable).orElse(false)) {
                    enable(id);
                }
            } catch (Exception e) {
                log.error("插件 {} 加载失败", id);
                log.error(e.getMessage(), e);
            }
        }
    }

    public synchronized LoadedPlugin load(String id) {
        PluginPaths.verifyId(id);
        LoadedPlugin existing = loaded.get(id);
        if (existing != null) {
            return existing;
        }
        PluginDescriptor descriptor = readInstalledDescriptor(id);
        PluginSource source = null;
        try {
            source = PluginSources.create(descriptor);
            Plugin plugin = source.load();
            PluginContextImpl context = new PluginContextImpl(descriptor, registry, MavenUtils.getVersion());
            plugin.onLoad(context);
            LoadedPlugin loadedPlugin = new LoadedPlugin(descriptor, plugin, context, source);
            loaded.put(id, loadedPlugin);
            PluginRecord record = registry.getOrCreate(id);
            if (!descriptor.script()) {
                record.setSha256(SecureUtil.sha256(PluginPaths.jarFile(id)));
            }
            registry.sync();
            log.info("插件已加载 {} {} ({})", descriptor.name(), descriptor.version(), descriptor.runtime());
            return loadedPlugin;
        } catch (Exception e) {
            if (source != null) {
                source.close();
            }
            throw new IllegalStateException("插件加载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取已安装插件的描述文件，并校验标识与接口版本。
     */
    private static PluginDescriptor readInstalledDescriptor(String id) {
        File descriptorFile = PluginPaths.descriptorFile(id);
        if (!descriptorFile.isFile()) {
            throw new IllegalArgumentException("插件缺少 plugin.json: " + id);
        }
        PluginDescriptor descriptor = GsonStatic.fromJson(
                FileUtil.readUtf8String(descriptorFile), PluginDescriptor.class);
        if (!id.equals(descriptor.id())) {
            throw new IllegalArgumentException("插件标识与目录不一致: " + id);
        }
        if (!descriptor.compatible()) {
            throw new IllegalStateException("插件接口版本不兼容，需要 " + descriptor.apiVersion()
                    + "，当前 " + PluginApi.VERSION);
        }
        if (descriptor.script() && StrUtil.isBlank(descriptor.entry())) {
            throw new IllegalArgumentException("脚本插件缺少入口文件");
        }
        if (!descriptor.script() && StrUtil.isBlank(descriptor.mainClass())) {
            throw new IllegalArgumentException("插件缺少入口类");
        }
        return descriptor;
    }

    public synchronized void enable(String id) {
        LoadedPlugin loadedPlugin = loaded.get(id);
        if (loadedPlugin == null) {
            loadedPlugin = load(id);
        }
        PluginContextImpl context = loadedPlugin.context();
        if (context.enabled()) {
            // 重复启用会重复注册扩展点与周期任务。
            return;
        }
        context.markEnabled(true);
        loadedPlugin.plugin().onEnable(context);
        scheduleTasks(loadedPlugin);
        PluginRecord record = registry.getOrCreate(id);
        record.setEnable(true);
        registry.sync();
    }

    public synchronized void disable(String id) {
        LoadedPlugin loadedPlugin = loaded.get(id);
        if (loadedPlugin == null) {
            return;
        }
        PluginContextImpl context = loadedPlugin.context();
        context.markEnabled(false);
        cancelTasks(id);
        try {
            loadedPlugin.plugin().onDisable(context);
        } catch (Exception e) {
            log.error("插件 {} 停用时出错", id);
            log.error(e.getMessage(), e);
        }
        context.clearRegistrations();
        PluginRecord record = registry.getOrCreate(id);
        record.setEnable(false);
        registry.sync();
    }

    /**
     * 卸载：停用、关闭类加载器并删除文件。
     */
    public synchronized void uninstall(String id) {
        PluginPaths.verifyId(id);
        disable(id);
        LoadedPlugin loadedPlugin = loaded.remove(id);
        if (loadedPlugin != null) {
            try {
                loadedPlugin.plugin().onUnload(loadedPlugin.context());
                loadedPlugin.source().close();
            } catch (Exception e) {
                log.warn("插件 {} 卸载时资源释放失败", id);
            }
        }
        cancelTasks(id);
        registry.remove(id);
        registry.sync();
        FileUtil.del(PluginPaths.dir(id));
    }

    /**
     * 重新加载插件（更新版本后使用）。
     */
    public synchronized void reload(String id) {
        boolean enabled = registry.get(id).map(PluginRecord::getEnable).orElse(false);
        cancelTasks(id);
        disable(id);
        LoadedPlugin removed = loaded.remove(id);
        if (removed != null) {
            try {
                removed.plugin().onUnload(removed.context());
                removed.source().close();
            } catch (Exception e) {
                log.warn("插件 {} 重载时资源释放失败", id);
            }
        }
        load(id);
        if (enabled) {
            enable(id);
        }
    }

    // ---- 调度：周期任务的注册与取消 ----

    private void scheduleTasks(LoadedPlugin loadedPlugin) {
        PluginContextImpl context = loadedPlugin.context();
        String id = loadedPlugin.descriptor().id();
        cancelTasks(id);
        List<ScheduledFuture<?>> futures = new ArrayList<>();
        for (ScheduledPluginTask task : context.tasks()) {
            long interval = Math.max(1, task.interval().toMillis());
            long delay = Math.max(0, task.initialDelay().toMillis());
            futures.add(scheduler.scheduleWithFixedDelay(() -> {
                if (!context.enabled()) {
                    return;
                }
                try {
                    task.run(context);
                } catch (Exception e) {
                    log.error("插件任务失败 {}#{}", id, task.id());
                    log.error(e.getMessage(), e);
                }
            }, delay, interval, TimeUnit.MILLISECONDS));
        }
        if (!futures.isEmpty()) {
            scheduledTasks.put(id, futures);
        }
    }

    /**
     * 取消插件已注册的调度任务。
     */
    private void cancelTasks(String id) {
        List<ScheduledFuture<?>> futures = scheduledTasks.remove(id);
        if (futures == null) {
            return;
        }
        for (ScheduledFuture<?> future : futures) {
            future.cancel(false);
        }
    }

    // ---- 描述：插件列表与展示信息 ----

    public Optional<LoadedPlugin> find(String id) {
        return Optional.ofNullable(loaded.get(id));
    }

    public List<PluginInfo> list() {
        List<PluginInfo> result = new ArrayList<>();
        for (File dir : Objects.requireNonNullElse(PluginPaths.root().listFiles(File::isDirectory), new File[0])) {
            String id = dir.getName();
            if (!id.matches(PluginPaths.ID_REG)) {
                continue;
            }
            result.add(describe(id));
        }
        return result;
    }

    public PluginInfo describe(String id) {
        PluginRecord record = registry.get(id).orElse(null);
        LoadedPlugin loadedPlugin = loaded.get(id);
        if (loadedPlugin != null) {
            PluginDescriptor descriptor = loadedPlugin.descriptor();
            PluginContextImpl context = loadedPlugin.context();
            return new PluginInfo(
                    descriptor.id(), descriptor.name(), descriptor.version(), descriptor.apiVersion(),
                    descriptor.author(), descriptor.description(), descriptor.homepage(),
                    descriptor.permissions(),
                    context.enabled() ? PluginState.ENABLED : PluginState.DISABLED,
                    "", record == null ? "" : record.getSource(),
                    record == null ? 0L : record.getInstalledAt(),
                    descriptor.updateUrl(),
                    record == null ? "" : StrUtil.nullToEmpty(record.getLatestVersion()),
                    record != null && Boolean.TRUE.equals(record.getUpdateAvailable()),
                    sections(context.sections()), record == null ? "" : StrUtil.nullToEmpty(record.getConfig()),
                    channels(context.notificationChannels()), endpoints(context.endpoints()),
                    descriptor.runtime(),
                    record == null || record.getGrantedPermissions() == null
                            ? ani.rss.plugin.api.PluginPermission.sanitize(descriptor.permissions())
                            : record.getGrantedPermissions()
            );
        }
        File descriptorFile = PluginPaths.descriptorFile(id);
        if (!descriptorFile.isFile()) {
            return new PluginInfo(id, id, "", 0, "", "", "", List.of(),
                    PluginState.ERROR, "缺少 plugin.json", "", 0L, "", "", false,
                    List.of(), "", List.of(), List.of(), "", List.of());
        }
        try {
            PluginDescriptor descriptor = GsonStatic.fromJson(FileUtil.readUtf8String(descriptorFile), PluginDescriptor.class);
            PluginInfo base = PluginInfo.from(descriptor);
            return new PluginInfo(base.id(), base.name(), base.version(), base.apiVersion(),
                    base.author(), base.description(), base.homepage(), base.permissions(),
                    descriptor.compatible() ? PluginState.DISABLED : PluginState.INCOMPATIBLE,
                    descriptor.compatible() ? "" : "插件接口版本不兼容",
                    record == null ? "" : StrUtil.nullToEmpty(record.getSource()),
                    record == null ? 0L : record.getInstalledAt(),
                    descriptor.updateUrl(), record == null ? "" : StrUtil.nullToEmpty(record.getLatestVersion()),
                    record != null && Boolean.TRUE.equals(record.getUpdateAvailable()),
                    List.of(), record == null ? "" : StrUtil.nullToEmpty(record.getConfig()),
                    List.of(), List.of(), descriptor.runtime(),
                    record == null || record.getGrantedPermissions() == null
                            ? ani.rss.plugin.api.PluginPermission.sanitize(descriptor.permissions())
                            : record.getGrantedPermissions());
        } catch (Exception e) {
            return new PluginInfo(id, id, "", 0, "", "", "", List.of(),
                    PluginState.ERROR, "plugin.json 解析失败", "", 0L, "", "", false,
                    List.of(), "", List.of(), List.of(), "", List.of());
        }
    }

    private static List<PluginInfo.SectionView> sections(List<SettingsSection> sections) {
        return sections.stream()
                .map(section -> new PluginInfo.SectionView(section.id(), section.title(), section.description(),
                        fields(section.fields())))
                .toList();
    }

    private static List<PluginInfo.EndpointView> endpoints(List<PluginEndpoint> endpoints) {
        return endpoints.stream()
                .map(endpoint -> new PluginInfo.EndpointView(endpoint.path(),
                        new ArrayList<>(endpoint.methods())))
                .toList();
    }

    private static List<PluginInfo.ChannelView> channels(List<NotificationChannel> channels) {
        return channels.stream()
                .map(channel -> new PluginInfo.ChannelView(channel.id(), channel.name(), fields(channel.fields())))
                .toList();
    }

    private static List<PluginInfo.FieldView> fields(List<ConfigField> fields) {
        return fields.stream()
                .map(field -> new PluginInfo.FieldView(field.key(), field.label(),
                        field.type().name(), StrUtil.nullToEmpty(field.placeholder()),
                        field.required(), field.options()))
                .toList();
    }

    /**
     * 更新插件记录，供市场安装后写入来源与校验值。
     */
    public void updateRecord(String id, Consumer<PluginRecord> consumer) {
        PluginRecord record = registry.getOrCreate(id);
        consumer.accept(record);
        registry.sync();
    }

    public PluginRegistry registry() {
        return registry;
    }

    // ---- 描述文件：读取 / 校验 / 入口检查 ----

    /**
     * 读取插件包内的描述文件，用于安装前校验。
     * <p>
     * Java 插件是包含 {@code plugin.json} 的 JAR；脚本插件是包含
     * {@code plugin.json} 与入口脚本的 ZIP 包。
     */
    public static PluginDescriptor readDescriptor(File jar) {
        String json;
        if (jar.isDirectory()) {
            File descriptorFile = new File(jar, PluginApi.DESCRIPTOR_FILE);
            if (!descriptorFile.isFile()) {
                throw new IllegalArgumentException("插件包缺少 plugin.json");
            }
            json = FileUtil.readUtf8String(descriptorFile);
            return validateDescriptor(json, jar);
        }
        try (JarFile jarFile = new JarFile(jar)) {
            // plugin.json 优先，未提供时兼容 manifest.json。
            JarEntry entry = jarFile.getJarEntry(PluginApi.DESCRIPTOR_FILE);
            if (entry == null) {
                entry = jarFile.getJarEntry(PluginApi.MANIFEST_FILE);
            }
            if (entry == null) {
                throw new IllegalArgumentException("插件包缺少 plugin.json 或 manifest.json");
            }
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                json = IoUtil.readUtf8(inputStream);
            }
            return validateDescriptor(json, jar);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("插件包读取失败: " + e.getMessage(), e);
        }
    }

    private static PluginDescriptor validateDescriptor(String json, File packageFile) {
        PluginDescriptor descriptor = GsonStatic.fromJson(json, PluginDescriptor.class);
        if (StrUtil.isBlank(descriptor.id())) {
            throw new IllegalArgumentException("plugin.json 缺少 id");
        }
        PluginPaths.verifyId(descriptor.id());
        if (descriptor.script()) {
            if (StrUtil.isBlank(descriptor.entry())) {
                throw new IllegalArgumentException("脚本插件缺少入口文件");
            }
            if (StrUtil.containsAny(descriptor.entry(), "..", "\\")) {
                throw new IllegalArgumentException("脚本入口路径不合法");
            }
            if (!hasEntry(packageFile, descriptor.entry())) {
                throw new IllegalArgumentException("插件包缺少入口脚本: " + descriptor.entry());
            }
            return descriptor;
        }
        if (StrUtil.isBlank(descriptor.mainClass())) {
            throw new IllegalArgumentException("plugin.json 缺少 mainClass");
        }
        return descriptor;
    }

    /**
     * 判断包内是否存在指定文件；目录形式直接检查文件系统。
     */
    private static boolean hasEntry(File packageFile, String entry) {
        if (packageFile.isDirectory()) {
            return new File(packageFile, entry).isFile();
        }
        try (JarFile jarFile = new JarFile(packageFile)) {
            return jarFile.getJarEntry(entry) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 扩展点分发：过滤 / 事件 / 通知 / 变量 / 拦截 / 接口 ----

    /**
     * 所有已启用插件的过滤器，按 order 排序。
     */
    public List<ItemFilter> itemFilters() {
        return loaded.values().stream()
                .filter(plugin -> plugin.context().enabled())
                .flatMap(plugin -> plugin.context().filters().stream())
                .sorted(Comparator.comparingInt(ItemFilter::order))
                .toList();
    }

    public List<SubscriptionListener> listeners() {
        return loaded.values().stream()
                .filter(plugin -> plugin.context().enabled())
                .flatMap(plugin -> plugin.context().listeners().stream())
                .toList();
    }

    /**
     * 插件通知渠道，key 为渠道 id。
     */
    public Map<String, NotificationChannel> notificationChannels() {
        Map<String, NotificationChannel> result = new LinkedHashMap<>();
        loaded.values().stream()
                .filter(plugin -> plugin.context().enabled())
                .forEach(plugin -> plugin.context().notificationChannels()
                        .forEach(channel -> result.put(channel.id(), channel)));
        return result;
    }

    /**
     * 所有已启用插件的模板变量提供方。
     */
    public List<TemplateVariableProvider> variableProviders() {
        return loaded.values().stream()
                .filter(plugin -> plugin.context().enabled())
                .flatMap(plugin -> plugin.context().variableProviders().stream())
                .toList();
    }

    /**
     * 判断某个拦截器所属插件是否获得了取消下载的权限。
     * <p>
     * 拦截器只负责修改任务；取消是破坏性操作，需要单独的 {@code DOWNLOAD_CANCEL} 权限。
     *
     * @param interceptorId 拦截器标识
     * @return 是否允许取消
     */
    public boolean cancelAllowed(String interceptorId) {
        return loaded.values().stream()
                .filter(plugin -> plugin.context().enabled())
                .filter(plugin -> plugin.context().downloadInterceptors().stream()
                        .anyMatch(interceptor -> interceptor.id().equals(interceptorId)))
                .anyMatch(plugin -> plugin.context().hasPermission(ani.rss.plugin.api.PluginPermission.DOWNLOAD_CANCEL));
    }

    /**
     * 所有已启用插件的下载拦截器，按 order 排序。
     */
    public List<DownloadInterceptor> downloadInterceptors() {
        return loaded.values().stream()
                .filter(plugin -> plugin.context().enabled())
                .flatMap(plugin -> plugin.context().downloadInterceptors().stream())
                .sorted(Comparator.comparingInt(DownloadInterceptor::order))
                .toList();
    }

    /**
     * 查找插件注册的 HTTP 接口。
     *
     * @param pluginId 插件标识
     * @param path     相对路径
     */
    public Optional<PluginEndpoint> endpoint(String pluginId, String path) {
        LoadedPlugin loadedPlugin = loaded.get(pluginId);
        if (loadedPlugin == null || !loadedPlugin.context().enabled()) {
            return Optional.empty();
        }
        String target = StrUtil.strip(path, "/");
        return loadedPlugin.context().endpoints().stream()
                .filter(endpoint -> StrUtil.strip(endpoint.path(), "/").equals(target))
                .findFirst();
    }

    /**
     * 分发订阅变更事件，异常只记录日志。
     */
    public void fireSubscriptionEvent(SubscriptionEvent event, ItemFilter.SubscriptionSnapshot snapshot) {
        for (SubscriptionListener listener : listeners()) {
            if (!listener.accepts(snapshot.id())) {
                continue;
            }
            try {
                switch (event) {
                    case CREATED -> listener.onCreated(snapshot);
                    case UPDATED -> listener.onUpdated(snapshot);
                    case DELETED -> listener.onDeleted(snapshot);
                }
            } catch (Exception e) {
                log.error("插件订阅事件处理失败 {}", listener.id());
                log.error(e.getMessage(), e);
            }
        }
    }

    public enum SubscriptionEvent {
        CREATED, UPDATED, DELETED
    }

    /**
     * 关闭调度器，供应用退出时调用。
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        scheduledTasks.values().forEach(futures -> futures.forEach(future -> future.cancel(false)));
        scheduledTasks.clear();
        scheduler.shutdownNow();
    }
}
