package ani.rss.plugin.runtime;

import ani.rss.plugin.api.ConfigField;
import ani.rss.plugin.api.DownloadInterceptor;
import ani.rss.plugin.api.ItemFilter;
import ani.rss.plugin.api.NotificationChannel;
import ani.rss.plugin.api.NotificationPayload;
import ani.rss.plugin.api.Plugin;
import ani.rss.plugin.api.PluginContext;
import ani.rss.plugin.api.PluginDescriptor;
import ani.rss.plugin.api.PluginEndpoint;
import ani.rss.plugin.api.ScheduledPluginTask;
import ani.rss.plugin.api.SettingsSection;
import ani.rss.plugin.api.SubscriptionListener;
import ani.rss.plugin.api.TemplateVariableProvider;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lua 插件的宿主适配层。
 * <p>
 * 入口脚本定义 {@code function on_enable() ... end} 等生命周期函数，并通过
 * {@code api.register_notification_channel{...}} 注册扩展点。脚本运行在 LuaJ 解释器里，
 * 不加载 {@code luajava} 库，因此拿不到任何 Java 类。
 */
@Slf4j
public class LuaPlugin implements Plugin {

    /**
     * 单次调用最长执行时间
     */
    private static final long TIMEOUT_MS = 5_000L;

    private final PluginDescriptor descriptor;
    private final String source;

    private Globals globals;

    public LuaPlugin(PluginDescriptor descriptor, File baseDir) {
        this.descriptor = descriptor;
        File entry = new File(baseDir, descriptor.entry());
        if (!entry.isFile()) {
            throw new IllegalArgumentException("Lua 插件缺少入口文件: " + descriptor.entry());
        }
        this.source = FileUtil.readUtf8String(entry);
    }

    @Override
    public void onLoad(PluginContext context) {
        guard(() -> {
            globals = sandboxedGlobals();
            globals.set("api", createApi(context));
            LuaValue chunk = globals.load(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                    descriptor.entry(), "t", globals);
            chunk.call();
            lifecycle("on_load");
            return null;
        });
    }

    @Override
    public void onEnable(PluginContext context) {
        guard(() -> {
            lifecycle("on_enable");
            return null;
        });
    }

    @Override
    public void onDisable(PluginContext context) {
        guard(() -> {
            lifecycle("on_disable");
            return null;
        });
    }

    @Override
    public void onUnload(PluginContext context) {
        guard(() -> {
            lifecycle("on_unload");
            return null;
        });
    }

    private interface Task {
        Object run();
    }

    private void guard(Task task) {
        try {
            withTimeout(task);
        } catch (Exception e) {
            log.error("插件 {} Lua 执行失败: {}", descriptor.id(), e.getMessage());
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 带超时的执行：DebugLib 每到一定指令数回调一次，超过截止时间就抛错。
     */
    private Object withTimeout(Task task) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        deadlineHolder.set(deadline);
        try {
            return task.run();
        } finally {
            deadlineHolder.remove();
        }
    }

    private static final ThreadLocal<Long> deadlineHolder = new ThreadLocal<>();

    /**
     * 只装载纯计算标准库，并封死文件与模块加载入口。
     * <p>
     * 不使用 {@code JsePlatform.standardGlobals()}：它包含 JseBaseLib（可读本地文件）
     * 以及 luajava 的 Java 类访问。这里改用通用 BaseLib，只保留字符串、表、数学、位运算
     * 与协程。PackageLib 无法直接省略——TableLib 会向 {@code package.loaded} 注册模块，
     * 因此装载后立即移除 {@code require}、{@code loadlib} 与搜索路径，使其只剩空壳。
     */
    private Globals sandboxedGlobals() {
        Globals g = new Globals();
        g.load(new org.luaj.vm2.lib.BaseLib());
        g.load(new org.luaj.vm2.lib.PackageLib());
        g.load(new org.luaj.vm2.lib.TableLib());
        g.load(new org.luaj.vm2.lib.StringLib());
        g.load(new org.luaj.vm2.lib.MathLib());
        g.load(new org.luaj.vm2.lib.Bit32Lib());
        g.load(new org.luaj.vm2.lib.CoroutineLib());
        // BaseLib 不注册编译器，需要显式安装；只编译一次脚本，不提供运行期编译入口。
        org.luaj.vm2.compiler.LuaC.install(g);
        // 必须走 load，DebugLib 要在这里拿到 Globals 引用才会被闭包调用。
        g.load(timeoutLib());
        g.set("dofile", LuaValue.NIL);
        g.set("loadfile", LuaValue.NIL);
        g.set("require", LuaValue.NIL);
        g.set("collectgarbage", LuaValue.NIL);
        g.set("module", LuaValue.NIL);
        LuaValue pkg = g.get("package");
        if (pkg.istable()) {
            pkg.set("loadlib", LuaValue.NIL);
            pkg.set("path", LuaValue.valueOf(""));
            pkg.set("cpath", LuaValue.valueOf(""));
            pkg.set("searchers", new LuaTable());
            pkg.set("loaders", new LuaTable());
        }
        return g;
    }

    private DebugLib timeoutLib() {
        return new DebugLib() {
            @Override
            public void onInstruction(int pc, Varargs v, int top) {
                Long deadline = deadlineHolder.get();
                if (deadline != null && System.currentTimeMillis() > deadline) {
                    throw new LuaError("Lua 脚本执行超时");
                }
                super.onInstruction(pc, v, top);
            }
        };
    }

    private void lifecycle(String name) {
        if (globals == null) {
            return;
        }
        LuaValue fn = globals.get(name);
        if (fn.isfunction()) {
            fn.call();
        }
    }

    private LuaTable createApi(PluginContext context) {
        LuaTable api = new LuaTable();

        api.set("log", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                context.log(arg.tojstring());
                return LuaValue.NIL;
            }
        });
        api.set("get_config", new org.luaj.vm2.lib.ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(StrUtil.nullToEmpty(context.getConfig()));
            }
        });
        api.set("save_config", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                context.saveConfig(arg.tojstring());
                return LuaValue.NIL;
            }
        });
        api.set("app_version", new org.luaj.vm2.lib.ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(context.appVersion());
            }
        });
        api.set("data_dir", new org.luaj.vm2.lib.ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(context.dataDir().getAbsolutePath());
            }
        });
        api.set("list_data_files", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return fromJava(context.listDataFiles(arg.isnil() ? "" : arg.tojstring()));
            }
        });
        api.set("read_data_file", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(context.readDataFile(arg.tojstring()));
            }
        });
        api.set("write_data_file", new org.luaj.vm2.lib.TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue path, LuaValue content) {
                context.writeDataFile(path.tojstring(), content.tojstring());
                return LuaValue.NIL;
            }
        });
        api.set("delete_data_file", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(context.deleteDataFile(arg.tojstring()));
            }
        });
        // 脚本可以按权限分支，例如缺少权限时退回内置行为。
        api.set("has_permission", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(ani.rss.plugin.api.PluginPermission.of(arg.tojstring())
                        .map(context::hasPermission)
                        .orElse(false));
            }
        });

        api.set("register_notification_channel", registrar(
                value -> context.registerNotificationChannel(channel(value))));
        api.set("register_item_filter", registrar(
                value -> context.registerItemFilter(filter(value))));
        api.set("register_subscription_listener", registrar(
                value -> context.registerSubscriptionListener(listener(value))));
        api.set("register_scheduled_task", registrar(
                value -> context.registerScheduledTask(task(value))));
        api.set("register_settings_section", registrar(
                value -> context.registerSettingsSection(section(value))));
        api.set("register_template_variables", registrar(
                value -> context.registerTemplateVariableProvider(provider(value))));
        api.set("register_download_interceptor", registrar(
                value -> context.registerDownloadInterceptor(interceptor(value))));
        api.set("register_endpoint", registrar(
                value -> context.registerEndpoint(endpoint(value))));
        return api;
    }

    private interface Registrar {
        void accept(LuaValue value);
    }

    private static VarArgFunction registrar(Registrar registrar) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue value = args.arg(1);
                if (value.isnil() || !value.istable()) {
                    throw new LuaError("注册项必须是 table");
                }
                registrar.accept(value);
                return LuaValue.NIL;
            }
        };
    }

    // ---- 数据转换 ----

    /**
     * Lua table 转普通 Java Map；数组形式的 table 转 List。
     */
    private static Object toJava(LuaValue value) {
        if (value == null || value.isnil()) {
            return null;
        }
        if (value.isboolean()) {
            return value.toboolean();
        }
        if (value.isnumber()) {
            double d = value.todouble();
            return d == Math.rint(d) && !Double.isInfinite(d) ? (Object) (long) d : d;
        }
        if (value.isstring()) {
            return value.tojstring();
        }
        if (value.istable()) {
            LuaTable table = value.checktable();
            if (isArray(table)) {
                List<Object> list = new ArrayList<>();
                for (int i = 1; i <= table.length(); i++) {
                    list.add(toJava(table.get(i)));
                }
                return list;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = table.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                map.put(key.tojstring(), toJava(next.arg(2)));
            }
            return map;
        }
        return value.tojstring();
    }

    private static boolean isArray(LuaTable table) {
        int length = table.length();
        if (length == 0) {
            return false;
        }
        LuaValue key = LuaValue.NIL;
        int count = 0;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            count++;
            if (!key.isint() || key.toint() < 1 || key.toint() > length) {
                return false;
            }
        }
        return count == length;
    }

    private static LuaTable fromJava(Object value) {
        LuaTable table = new LuaTable();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, v) -> table.set(String.valueOf(key), wrap(v)));
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                table.set(i + 1, wrap(list.get(i)));
            }
        }
        return table;
    }

    private static LuaValue wrap(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof Boolean b) {
            return LuaValue.valueOf(b);
        }
        if (value instanceof Number n) {
            return LuaValue.valueOf(n.doubleValue());
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return fromJava(value);
        }
        return LuaValue.valueOf(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(LuaValue value) {
        Object converted = toJava(value);
        if (converted instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, v) -> result.put(String.valueOf(key), v));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static int intOf(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static long longOf(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        return value instanceof Number n ? n.longValue() : fallback;
    }

    private static boolean boolOf(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean b ? b : fallback;
    }

    private static List<String> listOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapListOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> nested) {
                    Map<String, Object> converted = new LinkedHashMap<>();
                    nested.forEach((k, v) -> converted.put(String.valueOf(k), v));
                    result.add(converted);
                }
            }
            return result;
        }
        return List.of();
    }

    private static List<ConfigField> fields(Map<String, Object> map) {
        List<ConfigField> fields = new ArrayList<>();
        for (Map<String, Object> item : mapListOf(map, "fields")) {
            ConfigField.FieldType type;
            try {
                String name = str(item, "type").trim().toUpperCase();
                type = name.isEmpty() ? ConfigField.FieldType.TEXT : ConfigField.FieldType.valueOf(name);
            } catch (IllegalArgumentException e) {
                type = ConfigField.FieldType.TEXT;
            }
            fields.add(new ConfigField(str(item, "key"), str(item, "label"), type,
                    str(item, "placeholder"), boolOf(item, "required", false), listOf(item, "options")));
        }
        return fields;
    }

    private static Map<String, Object> subscriptionMap(ItemFilter.SubscriptionSnapshot s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.id());
        map.put("title", s.title());
        map.put("group", s.group());
        map.put("season", s.season());
        map.put("weekLabel", s.weekLabel());
        map.put("currentEpisode", s.currentEpisode());
        map.put("totalEpisode", s.totalEpisode());
        map.put("tags", s.tags());
        return map;
    }

    private static Map<String, Object> itemMap(ItemFilter.ItemSnapshot s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", s.title());
        map.put("rename", s.rename());
        map.put("subgroup", s.subgroup());
        map.put("episode", s.episode());
        map.put("master", s.master());
        map.put("size", s.size());
        map.put("magnet", s.magnet());
        return map;
    }

    /**
     * 调用 table 中的函数字段；不存在时返回 nil。
     */
    private static LuaValue callFn(LuaTable table, String name, LuaValue... args) {
        LuaValue fn = table.get(name);
        if (!fn.isfunction()) {
            return LuaValue.NIL;
        }
        return ((LuaFunction) fn).invoke(LuaValue.varargsOf(args)).arg1();
    }

    // ---- 扩展点适配 ----

    private NotificationChannel channel(LuaValue value) {
        LuaTable table = value.checktable();
        return new NotificationChannel() {
            @Override
            public String id() {
                return table.get("id").tojstring();
            }

            @Override
            public String name() {
                return table.get("name").tojstring();
            }

            @Override
            public List<ConfigField> fields() {
                return LuaPlugin.fields(map(value));
            }

            @Override
            public void send(NotificationPayload payload, Map<String, String> options) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("status", payload.status());
                p.put("statusLabel", payload.statusLabel());
                p.put("title", payload.title());
                p.put("subgroup", payload.subgroup());
                p.put("text", payload.text());
                p.put("episode", payload.episode());
                p.put("coverUrl", payload.coverUrl());
                p.put("tags", payload.tags());
                callFn(table, "send", fromJava(p), fromJava(options));
            }
        };
    }

    private ItemFilter filter(LuaValue value) {
        LuaTable table = value.checktable();
        return new ItemFilter() {
            @Override
            public String id() {
                return table.get("id").tojstring();
            }

            @Override
            public int order() {
                return intOf(map(value), "order", 100);
            }

            @Override
            public Decision decide(ItemFilter.SubscriptionSnapshot ani, ItemFilter.ItemSnapshot item, Map<String, Object> context) {
                LuaValue result = callFn(table, "decide",
                        fromJava(subscriptionMap(ani)), fromJava(itemMap(item)), fromJava(context));
                String text = result.isnil() ? "" : result.tojstring().trim().toUpperCase();
                try {
                    return Decision.valueOf(text);
                } catch (IllegalArgumentException e) {
                    return Decision.ABSTAIN;
                }
            }
        };
    }

    private SubscriptionListener listener(LuaValue value) {
        LuaTable table = value.checktable();
        return new SubscriptionListener() {
            @Override
            public String id() {
                return table.get("id").tojstring();
            }

            @Override
            public void onCreated(ItemFilter.SubscriptionSnapshot subscription) {
                callFn(table, "on_created", fromJava(subscriptionMap(subscription)));
            }

            @Override
            public void onUpdated(ItemFilter.SubscriptionSnapshot subscription) {
                callFn(table, "on_updated", fromJava(subscriptionMap(subscription)));
            }

            @Override
            public void onDeleted(ItemFilter.SubscriptionSnapshot subscription) {
                callFn(table, "on_deleted", fromJava(subscriptionMap(subscription)));
            }
        };
    }

    private ScheduledPluginTask task(LuaValue value) {
        LuaTable table = value.checktable();
        return new ScheduledPluginTask() {
            @Override
            public String id() {
                return table.get("id").tojstring();
            }

            @Override
            public Duration interval() {
                return Duration.ofMinutes(Math.max(1, longOf(map(value), "interval_minutes", 30)));
            }

            @Override
            public Duration initialDelay() {
                long delay = longOf(map(value), "initial_delay_minutes", -1);
                return delay < 0 ? interval() : Duration.ofMinutes(delay);
            }

            @Override
            public void run(PluginContext context) {
                callFn(table, "run", LuaValue.valueOf(context.descriptor().id()));
            }
        };
    }

    private static SettingsSection section(LuaValue value) {
        Map<String, Object> map = map(value);
        return new SettingsSection(str(map, "id"), str(map, "title"),
                str(map, "description"), fields(map));
    }

    private TemplateVariableProvider provider(LuaValue value) {
        LuaTable table = value.checktable();
        return new TemplateVariableProvider() {
            @Override
            public String id() {
                return table.get("id").tojstring();
            }

            @Override
            public Map<String, String> variables(ItemFilter.SubscriptionSnapshot ani) {
                LuaValue result = callFn(table, "variables", fromJava(subscriptionMap(ani)));
                Map<String, String> variables = new LinkedHashMap<>();
                Object converted = toJava(result);
                if (converted instanceof Map<?, ?> values) {
                    values.forEach((key, v) ->
                            variables.put(String.valueOf(key), v == null ? "" : String.valueOf(v)));
                }
                return variables;
            }
        };
    }

    private DownloadInterceptor interceptor(LuaValue value) {
        LuaTable table = value.checktable();
        return new DownloadInterceptor() {
            @Override
            public String id() {
                return table.get("id").tojstring();
            }

            @Override
            public int order() {
                return intOf(map(value), "order", 100);
            }

            @Override
            public DownloadCandidate intercept(DownloadCandidate candidate, Map<String, Object> context) {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("hash", candidate.hash());
                snapshot.put("name", candidate.name());
                snapshot.put("title", candidate.title());
                snapshot.put("subgroup", candidate.subgroup());
                snapshot.put("episode", candidate.episode());
                snapshot.put("savePath", candidate.savePath());
                snapshot.put("tags", candidate.tags());
                snapshot.put("magnet", candidate.magnet());
                snapshot.put("size", candidate.size());
                snapshot.put("subscription", subscriptionMap(candidate.subscription()));
                LuaValue result = callFn(table, "intercept", fromJava(snapshot), fromJava(context));
                if (result.isnil()) {
                    return null;
                }
                Map<String, Object> changed = map(result);
                return candidate
                        .withSavePath(StrUtil.blankToDefault(str(changed, "savePath"), candidate.savePath()))
                        .withName(StrUtil.blankToDefault(str(changed, "name"), candidate.name()));
            }
        };
    }

    private PluginEndpoint endpoint(LuaValue value) {
        LuaTable table = value.checktable();
        return new PluginEndpoint() {
            @Override
            public String path() {
                return table.get("path").tojstring();
            }

            @Override
            public Set<String> methods() {
                List<String> methods = listOf(map(value), "methods");
                return methods.isEmpty() ? Set.of("POST") : Set.copyOf(methods);
            }

            @Override
            public String handle(EndpointRequest request) {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("method", request.method());
                snapshot.put("path", request.path());
                snapshot.put("query", request.query());
                snapshot.put("body", request.body());
                snapshot.put("contentType", request.contentType());
                LuaValue result = callFn(table, "handle", fromJava(snapshot));
                return result.isnil() ? "" : result.tojstring();
            }
        };
    }
}
