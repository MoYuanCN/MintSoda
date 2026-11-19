package ani.rss.plugin.runtime;

import ani.rss.plugin.api.ConfigField;
import ani.rss.plugin.api.DownloadInterceptor;
import ani.rss.plugin.api.DownloadListener;
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
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 脚本插件的宿主适配层。
 * <p>
 * 入口脚本定义 {@code onLoad/onEnable/onDisable/onUnload} 生命周期函数，并通过
 * {@code api.registerXxx(obj)} 注册扩展点。脚本只接触宿主注入的纯数据对象，
 * 看不到任何 Java 类。
 */
@Slf4j
public class ScriptPlugin implements Plugin {

    private final PluginDescriptor descriptor;
    private final String source;
    private final ScriptSandbox sandbox = new ScriptSandbox();

    private Scriptable scope;

    public ScriptPlugin(PluginDescriptor descriptor, File baseDir) {
        this.descriptor = descriptor;
        File entry = new File(baseDir, descriptor.entry());
        if (!entry.isFile()) {
            throw new IllegalArgumentException("脚本插件缺少入口文件: " + descriptor.entry());
        }
        this.source = FileUtil.readUtf8String(entry);
    }

    @Override
    public void onLoad(PluginContext context) {
        sandbox.invoke(cx -> {
            scope = cx.initSafeStandardObjects();
            ScriptableObject.putProperty(scope, "__api", createApi(cx, context));
            ScriptableObject.putProperty(scope, "__descriptor", descriptorObject(cx));
            cx.evaluateString(scope, source, descriptor.entry(), 1, null);
            lifecycle(cx, "onLoad");
            return null;
        });
    }

    @Override
    public void onEnable(PluginContext context) {
        invoke(cx -> {
            lifecycle(cx, "onEnable");
            return null;
        });
    }

    @Override
    public void onDisable(PluginContext context) {
        lifecycleSafe("onDisable");
    }

    @Override
    public void onUnload(PluginContext context) {
        lifecycleSafe("onUnload");
    }

    private void invoke(ScriptSandbox.ScriptTask<?> task) {
        if (scope == null) {
            return;
        }
        sandbox.invoke(task);
    }

    private void lifecycleSafe(String name) {
        try {
            invoke(cx -> {
                lifecycle(cx, name);
                return null;
            });
        } catch (Exception e) {
            log.error("插件 {} 脚本回调失败 {}", descriptor.id(), name);
            log.error(e.getMessage(), e);
        }
    }

    private void lifecycle(Context cx, String name) {
        Object fn = ScriptableObject.getProperty(scope, name);
        if (fn instanceof Function function) {
            function.call(cx, scope, scope, new Object[0]);
        }
    }

    /**
     * 调用脚本对象上的函数；返回值为已转换的普通 Java 值。
     */
    private Object callFunction(Object fn, List<Object> args) {
        if (!(fn instanceof Function function)) {
            return null;
        }
        Object[] holder = new Object[1];
        sandbox.invoke(cx -> {
            Object[] converted = args.stream().map(arg -> toNative(cx, arg)).toArray();
            holder[0] = function.call(cx, scope, scope, converted);
            return null;
        });
        return fromJs(holder[0]);
    }

    /**
     * 把宿主数据转换为原生脚本值。
     * <p>
     * 沙箱拒绝一切 Java 类，直接传 {@code Map}/{@code List} 会被 Rhino 包装成
     * {@code NativeJavaObject}，脚本一访问属性就会触发类检查。这里递归复制成
     * 原生对象与数组，脚本只能看到纯数据。
     */
    private Scriptable toNative(Context cx, Object value) {
        return toNative(cx, scope, value);
    }

    private static Scriptable toNative(Context cx, Scriptable scope, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Scriptable obj = cx.newObject(scope);
            map.forEach((key, v) -> obj.put(String.valueOf(key), obj,
                    isScalar(v) ? v : toNative(cx, scope, v)));
            return obj;
        }
        if (value instanceof List<?> list) {
            Object[] values = list.stream()
                    .map(item -> isScalar(item) ? item : toNative(cx, scope, item))
                    .toArray();
            return cx.newArray(scope, values);
        }
        return null;
    }

    private static boolean isScalar(Object value) {
        return value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean;
    }

    private void callVoid(Object fn, List<Object> args) {
        callFunction(fn, args);
    }

    private Scriptable createApi(Context cx, PluginContext context) {
        Scriptable api = cx.newObject(scope);

        api.put("log", api, function((cx1, args) -> {
            context.log(text(args));
            return Undefined.instance;
        }));
        api.put("logWarn", api, function((cx1, args) -> {
            context.logWarn(text(args));
            return Undefined.instance;
        }));
        api.put("logError", api, function((cx1, args) -> {
            context.logError(text(args));
            return Undefined.instance;
        }));
        api.put("logDebug", api, function((cx1, args) -> {
            context.logDebug(text(args));
            return Undefined.instance;
        }));
        api.put("getConfig", api, function((cx1, args) -> context.getConfig()));
        api.put("saveConfig", api, function((cx1, args) -> {
            context.saveConfig(args.length == 0 || args[0] == null ? "" : String.valueOf(args[0]));
            return Undefined.instance;
        }));
        api.put("appVersion", api, function((cx1, args) -> context.appVersion()));
        api.put("dataDir", api, function((cx1, args) -> context.dataDir().getAbsolutePath()));
        api.put("listDataFiles", api, function((cx1, args) -> context.listDataFiles(
                args.length == 0 || args[0] == null ? "" : String.valueOf(args[0]))));
        api.put("readDataFile", api, function((cx1, args) -> context.readDataFile(
                args.length == 0 || args[0] == null ? "" : String.valueOf(args[0]))));
        api.put("writeDataFile", api, function((cx1, args) -> {
            context.writeDataFile(
                    args.length == 0 || args[0] == null ? "" : String.valueOf(args[0]),
                    args.length < 2 || args[1] == null ? "" : String.valueOf(args[1]));
            return Undefined.instance;
        }));
        api.put("deleteDataFile", api, function((cx1, args) -> context.deleteDataFile(
                args.length == 0 || args[0] == null ? "" : String.valueOf(args[0]))));
        // 脚本可以按权限分支，例如缺少权限时退回内置行为。
        api.put("hasPermission", api, function((cx1, args) -> args.length == 0 ? Boolean.FALSE
                : ani.rss.plugin.api.PluginPermission.of(String.valueOf(args[0]))
                .map(context::hasPermission)
                .orElse(Boolean.FALSE)));

        api.put("notificationChannel", api, function((cx1, args) ->
                register(args, value -> context.registerNotificationChannel(channel(value)))));
        api.put("itemFilter", api, function((cx1, args) ->
                register(args, value -> context.registerItemFilter(filter(value)))));
        api.put("subscriptionListener", api, function((cx1, args) ->
                register(args, value -> context.registerSubscriptionListener(listener(value)))));
        api.put("scheduledTask", api, function((cx1, args) ->
                register(args, value -> context.registerScheduledTask(task(value)))));
        api.put("settingsSection", api, function((cx1, args) ->
                register(args, value -> context.registerSettingsSection(section(value)))));
        api.put("templateVariables", api, function((cx1, args) ->
                register(args, value -> context.registerTemplateVariableProvider(provider(value)))));
        api.put("downloadInterceptor", api, function((cx1, args) ->
                register(args, value -> context.registerDownloadInterceptor(interceptor(value)))));
        api.put("downloadListener", api, function((cx1, args) ->
                register(args, value -> context.registerDownloadListener(downloadListener(value)))));
        api.put("endpoint", api, function((cx1, args) ->
                register(args, value -> context.registerEndpoint(endpoint(value)))));
        return api;
    }

    private static String text(Object[] args) {
        if (args.length == 0 || args[0] == null || args[0] == Undefined.instance) {
            return "";
        }
        return String.valueOf(args[0]);
    }

    private static Object register(Object[] args, java.util.function.Consumer<Object> consumer) {
        if (args.length == 0 || args[0] == null || args[0] == Undefined.instance) {
            throw new IllegalArgumentException("注册项不能为空");
        }
        consumer.accept(args[0]);
        return Undefined.instance;
    }

    private interface ScriptFn {
        Object apply(Context cx, Object[] args);
    }

    private static BaseFunction function(ScriptFn fn) {
        return new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                return fn.apply(cx, args);
            }
        };
    }

    private Scriptable descriptorObject(Context cx) {
        Scriptable obj = cx.newObject(scope);
        obj.put("id", obj, descriptor.id());
        obj.put("name", obj, descriptor.name());
        obj.put("version", obj, descriptor.version());
        obj.put("apiVersion", obj, descriptor.apiVersion());
        obj.put("author", obj, StrUtil.nullToEmpty(descriptor.author()));
        obj.put("description", obj, StrUtil.nullToEmpty(descriptor.description()));
        return obj;
    }

    // ---- 脚本对象到宿主接口的适配 ----

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!(value instanceof Scriptable scriptable)) {
            return map;
        }
        for (Object id : scriptable.getIds()) {
            String key = String.valueOf(id);
            Object property = ScriptableObject.getProperty(scriptable, key);
            if (property != Scriptable.NOT_FOUND) {
                map.put(key, property);
            }
        }
        return map;
    }

    /**
     * 转换脚本值为普通 Java 值，函数保持为可调用对象。
     */
    private static Object fromJs(Object value) {
        if (value == null || value == Undefined.instance || value == Scriptable.NOT_FOUND) {
            return null;
        }
        if (value instanceof Function) {
            return value;
        }
        if (value instanceof Scriptable scriptable) {
            String className = scriptable.getClassName();
            if ("Array".equals(className)) {
                Object lengthObj = ScriptableObject.getProperty(scriptable, "length");
                int length = lengthObj instanceof Number number ? number.intValue() : 0;
                List<Object> list = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    list.add(fromJs(ScriptableObject.getProperty(scriptable, i)));
                }
                return list;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (Object id : scriptable.getIds()) {
                String key = String.valueOf(id);
                map.put(key, fromJs(ScriptableObject.getProperty(scriptable, key)));
            }
            return map;
        }
        if (value instanceof Double d) {
            return !d.isInfinite() && !d.isNaN() && d == Math.rint(d) ? (Object) d.longValue() : d;
        }
        return value;
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static int intOf(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long longOf(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
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

    private NotificationChannel channel(Object value) {
        Map<String, Object> map = asMap(value);
        return new NotificationChannel() {
            @Override
            public String id() {
                return str(map, "id");
            }

            @Override
            public String name() {
                return str(map, "name");
            }

            @Override
            public List<ConfigField> fields() {
                return ScriptPlugin.fields(map);
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
                callVoid(map.get("send"), List.of(p, options));
            }
        };
    }

    private ItemFilter filter(Object value) {
        Map<String, Object> map = asMap(value);
        return new ItemFilter() {
            @Override
            public String id() {
                return str(map, "id");
            }

            @Override
            public int order() {
                return intOf(map, "order", 100);
            }

            @Override
            public Decision decide(ItemFilter.SubscriptionSnapshot ani, ItemFilter.ItemSnapshot item, Map<String, Object> context) {
                Object result = callFunction(map.get("decide"),
                        List.of(subscriptionMap(ani), itemMap(item), context));
                String text = result == null ? "" : String.valueOf(result).trim().toUpperCase();
                try {
                    return Decision.valueOf(text);
                } catch (IllegalArgumentException e) {
                    return Decision.ABSTAIN;
                }
            }
        };
    }

    private SubscriptionListener listener(Object value) {
        Map<String, Object> map = asMap(value);
        return new SubscriptionListener() {
            @Override
            public String id() {
                return str(map, "id");
            }

            @Override
            public void onCreated(ItemFilter.SubscriptionSnapshot subscription) {
                callVoid(map.get("onCreated"), List.of(subscriptionMap(subscription)));
            }

            @Override
            public void onUpdated(ItemFilter.SubscriptionSnapshot subscription) {
                callVoid(map.get("onUpdated"), List.of(subscriptionMap(subscription)));
            }

            @Override
            public void onDeleted(ItemFilter.SubscriptionSnapshot subscription) {
                callVoid(map.get("onDeleted"), List.of(subscriptionMap(subscription)));
            }
        };
    }

    private ScheduledPluginTask task(Object value) {
        Map<String, Object> map = asMap(value);
        return new ScheduledPluginTask() {
            @Override
            public String id() {
                return str(map, "id");
            }

            @Override
            public Duration interval() {
                return Duration.ofMinutes(Math.max(1, longOf(map, "intervalMinutes", 30)));
            }

            @Override
            public Duration initialDelay() {
                long delay = longOf(map, "initialDelayMinutes", -1);
                return delay < 0 ? interval() : Duration.ofMinutes(delay);
            }

            @Override
            public void run(PluginContext context) {
                callVoid(map.get("run"), List.of(context.descriptor().id()));
            }
        };
    }

    private static SettingsSection section(Object value) {
        Map<String, Object> map = asMap(value);
        return new SettingsSection(str(map, "id"), str(map, "title"),
                str(map, "description"), fields(map));
    }

    private TemplateVariableProvider provider(Object value) {
        Map<String, Object> map = asMap(value);
        return new TemplateVariableProvider() {
            @Override
            public String id() {
                return str(map, "id");
            }

            @Override
            public Map<String, String> variables(ItemFilter.SubscriptionSnapshot ani) {
                Object result = callFunction(map.get("variables"), List.of(subscriptionMap(ani)));
                Map<String, String> variables = new LinkedHashMap<>();
                if (result instanceof Map<?, ?> values) {
                    values.forEach((key, v) ->
                            variables.put(String.valueOf(key), v == null ? "" : String.valueOf(v)));
                }
                return variables;
            }
        };
    }

    private DownloadInterceptor interceptor(Object value) {
        Map<String, Object> map = asMap(value);
        return new DownloadInterceptor() {
            @Override
            public String id() {
                return str(map, "id");
            }

            @Override
            public int order() {
                return intOf(map, "order", 100);
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
                Object result = callFunction(map.get("intercept"), List.of(snapshot, context));
                if (result == null) {
                    return null;
                }
                Map<String, Object> changed = new LinkedHashMap<>();
                if (result instanceof Map<?, ?> values) {
                    values.forEach((key, v) -> changed.put(String.valueOf(key), v));
                }
                return candidate
                        .withSavePath(StrUtil.blankToDefault(str(changed, "savePath"), candidate.savePath()))
                        .withName(StrUtil.blankToDefault(str(changed, "name"), candidate.name()));
            }
        };
    }

    private DownloadListener downloadListener(Object value) {
        Map<String, Object> map = asMap(value);
        return new DownloadListener() {
            @Override
            public String id() {
                return str(map, "id");
            }

            @Override
            public void onAdded(DownloadSnapshot task, Map<String, Object> context) {
                callVoid(map.get("onAdded"), List.of(downloadSnapshotMap(task), context));
            }

            @Override
            public void onCompleted(DownloadSnapshot task, Map<String, Object> context) {
                callVoid(map.get("onCompleted"), List.of(downloadSnapshotMap(task), context));
            }

            @Override
            public void onFailed(DownloadSnapshot task, Map<String, Object> context) {
                callVoid(map.get("onFailed"), List.of(downloadSnapshotMap(task), context));
            }
        };
    }

    private Map<String, Object> downloadSnapshotMap(DownloadListener.DownloadSnapshot task) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hash", task.hash());
        snapshot.put("name", task.name());
        snapshot.put("title", task.title());
        snapshot.put("subgroup", task.subgroup());
        snapshot.put("episode", task.episode());
        snapshot.put("savePath", task.savePath());
        snapshot.put("tags", task.tags());
        snapshot.put("size", task.size());
        snapshot.put("subscription", task.subscription() == null ? null : subscriptionMap(task.subscription()));
        return snapshot;
    }

    private PluginEndpoint endpoint(Object value) {
        Map<String, Object> map = asMap(value);
        return new PluginEndpoint() {
            @Override
            public String path() {
                return str(map, "path");
            }

            @Override
            public Set<String> methods() {
                List<String> methods = listOf(map, "methods");
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
                Object result = callFunction(map.get("handle"), List.of(snapshot));
                return result == null ? "" : String.valueOf(result);
            }
        };
    }
}
