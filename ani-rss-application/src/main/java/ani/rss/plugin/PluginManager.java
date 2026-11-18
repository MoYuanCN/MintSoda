package ani.rss.plugin;

import ani.rss.commons.GsonStatic;
import ani.rss.commons.MavenUtils;
import ani.rss.plugin.api.*;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
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
    private final PluginRegistry registry = PluginRegistry.load();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "plugin-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    public record LoadedPlugin(PluginDescriptor descriptor, Plugin plugin, PluginContextImpl context,
                               PluginClassLoader classLoader, URL jarUrl) {
    }

    /**
     * 启动时加载全部已安装插件，并启用标记为启用的插件。
     */
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

    public synchronized LoadedPlugin load(String id) {
        PluginPaths.verifyId(id);
        LoadedPlugin existing = loaded.get(id);
        if (existing != null) {
            return existing;
        }
        File jar = PluginPaths.jarFile(id);
        if (!jar.isFile()) {
            throw new IllegalArgumentException("插件文件不存在: " + id);
        }
        File descriptorFile = PluginPaths.descriptorFile(id);
        if (!descriptorFile.isFile()) {
            throw new IllegalArgumentException("插件缺少 plugin.json: " + id);
        }
        PluginDescriptor descriptor = GsonStatic.fromJson(FileUtil.readUtf8String(descriptorFile), PluginDescriptor.class);
        if (!id.equals(descriptor.id())) {
            throw new IllegalArgumentException("插件标识与目录不一致: " + id);
        }
        if (!descriptor.compatible()) {
            throw new IllegalStateException("插件接口版本不兼容，需要 " + descriptor.apiVersion()
                    + "，当前 " + PluginApi.VERSION);
        }
        try {
            URL url = jar.toURI().toURL();
            PluginClassLoader classLoader = new PluginClassLoader(id, url, PluginManager.class.getClassLoader());
            Class<?> mainClass = classLoader.loadClass(descriptor.mainClass());
            if (!Plugin.class.isAssignableFrom(mainClass)) {
                throw new IllegalArgumentException("插件入口未实现 Plugin 接口: " + descriptor.mainClass());
            }
            Plugin plugin = (Plugin) mainClass.getDeclaredConstructor().newInstance();
            PluginContextImpl context = new PluginContextImpl(descriptor, registry, MavenUtils.getVersion());
            plugin.onLoad(context);
            LoadedPlugin loadedPlugin = new LoadedPlugin(descriptor, plugin, context, classLoader, url);
            loaded.put(id, loadedPlugin);
            PluginRecord record = registry.getOrCreate(id);
            record.setSha256(SecureUtil.sha256(jar));
            registry.sync();
            log.info("插件已加载 {} {}", descriptor.name(), descriptor.version());
            return loadedPlugin;
        } catch (Exception e) {
            throw new IllegalStateException("插件加载失败: " + e.getMessage(), e);
        }
    }

    public synchronized void enable(String id) {
        LoadedPlugin loadedPlugin = loaded.get(id);
        if (loadedPlugin == null) {
            loadedPlugin = load(id);
        }
        PluginContextImpl context = loadedPlugin.context();
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
                loadedPlugin.classLoader().close();
            } catch (Exception e) {
                log.warn("插件 {} 卸载时资源释放失败", id);
            }
        }
        registry.remove(id);
        registry.sync();
        FileUtil.del(PluginPaths.dir(id));
    }

    /**
     * 重新加载插件（更新版本后使用）。
     */
    public synchronized void reload(String id) {
        boolean enabled = registry.get(id).map(PluginRecord::getEnable).orElse(false);
        disable(id);
        LoadedPlugin removed = loaded.remove(id);
        if (removed != null) {
            try {
                removed.plugin().onUnload(removed.context());
                removed.classLoader().close();
            } catch (Exception e) {
                log.warn("插件 {} 重载时资源释放失败", id);
            }
        }
        load(id);
        if (enabled) {
            enable(id);
        }
    }

    private void scheduleTasks(LoadedPlugin loadedPlugin) {
        PluginContextImpl context = loadedPlugin.context();
        String id = loadedPlugin.descriptor().id();
        for (ScheduledPluginTask task : context.tasks()) {
            long interval = Math.max(1, task.interval().toMillis());
            long delay = Math.max(0, task.initialDelay().toMillis());
            scheduler.scheduleWithFixedDelay(() -> {
                if (!context.enabled()) {
                    return;
                }
                try {
                    task.run(context);
                } catch (Exception e) {
                    log.error("插件任务失败 {}#{}", id, task.id());
                    log.error(e.getMessage(), e);
                }
            }, delay, interval, TimeUnit.MILLISECONDS);
        }
    }

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
                    channels(context.notificationChannels())
            );
        }
        File descriptorFile = PluginPaths.descriptorFile(id);
        if (!descriptorFile.isFile()) {
            return new PluginInfo(id, id, "", 0, "", "", "", List.of(),
                    PluginState.ERROR, "缺少 plugin.json", "", 0L, "", "", false,
                    List.of(), "", List.of());
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
                    List.of());
        } catch (Exception e) {
            return new PluginInfo(id, id, "", 0, "", "", "", List.of(),
                    PluginState.ERROR, "plugin.json 解析失败", "", 0L, "", "", false,
                    List.of(), "", List.of());
        }
    }

    private static List<PluginInfo.SectionView> sections(List<SettingsSection> sections) {
        return sections.stream()
                .map(section -> new PluginInfo.SectionView(section.id(), section.title(), section.description(),
                        fields(section.fields())))
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

    /**
     * 读取插件包内的描述文件，用于安装前校验。
     */
    public static PluginDescriptor readDescriptor(File jar) {
        try (JarFile jarFile = new JarFile(jar)) {
            JarEntry entry = jarFile.getJarEntry(PluginApi.DESCRIPTOR_FILE);
            if (entry == null) {
                throw new IllegalArgumentException("插件包缺少 plugin.json");
            }
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                PluginDescriptor descriptor = GsonStatic.fromJson(
                        IoUtil.readUtf8(inputStream), PluginDescriptor.class);
                if (StrUtil.isBlank(descriptor.id()) || StrUtil.isBlank(descriptor.mainClass())) {
                    throw new IllegalArgumentException("plugin.json 缺少 id 或 mainClass");
                }
                PluginPaths.verifyId(descriptor.id());
                return descriptor;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("插件包读取失败: " + e.getMessage(), e);
        }
    }

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
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
