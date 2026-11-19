package ani.rss.plugin.api;

import java.util.List;

/**
 * 插件描述信息，来自插件包内的 {@code plugin.json}。
 *
 * @param id             插件唯一标识，仅允许字母、数字、点、下划线与连字符
 * @param name           显示名称
 * @param version        插件版本
 * @param apiVersion     插件编译时依赖的接口版本
 * @param mainClass      {@link Plugin} 实现类全限定名
 * @param author         作者
 * @param description    简介
 * @param homepage       项目主页，可为空
 * @param updateUrl      自动更新检查地址，可为空
 * @param permissions    插件声明的能力，仅用于展示与审计
 * @param minAppVersion  允许运行的最低宿主张版本，可为空
 * @param runtime        运行时：JAVA 或 JAVASCRIPT，缺省为 JAVA
 * @param entry          JS 插件的入口文件，缺省 main.js；JAVA 插件忽略
 */
public record PluginDescriptor(
        String id,
        String name,
        String version,
        Integer apiVersion,
        String mainClass,
        String author,
        String description,
        String homepage,
        String updateUrl,
        List<String> permissions,
        String minAppVersion,
        String runtime,
        String entry
) {
    public static final String RUNTIME_JAVA = "JAVA";
    public static final String RUNTIME_JAVASCRIPT = "JAVASCRIPT";
    public static final String RUNTIME_LUA = "LUA";

    public PluginDescriptor {
        // 描述文件常用连字符与历史别名，统一成枚举的标准标识，避免权限在安装时被丢弃。
        permissions = PluginPermission.sanitize(permissions);
        // compact 构造器里 script() 还读不到字段，先归一化到局部变量。
        String normalizedRuntime = runtime == null || runtime.isBlank()
                ? RUNTIME_JAVA
                : runtime.trim().toUpperCase(java.util.Locale.ROOT);
        boolean script = RUNTIME_JAVASCRIPT.equals(normalizedRuntime) || RUNTIME_LUA.equals(normalizedRuntime);
        String defaultEntry = RUNTIME_LUA.equals(normalizedRuntime) ? "main.lua" : "main.js";
        runtime = normalizedRuntime;
        // Java 插件只用 mainClass，入口字段无意义。
        entry = !script ? "" : (entry == null || entry.isBlank() ? defaultEntry : entry.trim());
    }

    public boolean compatible() {
        return apiVersion != null && apiVersion <= PluginApi.VERSION;
    }

    /**
     * 是否为脚本插件（JavaScript 或 Lua），脚本插件以 ZIP 分发并解包到插件目录
     */
    public boolean script() {
        return RUNTIME_JAVASCRIPT.equals(runtime) || RUNTIME_LUA.equals(runtime);
    }

    /**
     * 是否为 Lua 插件
     */
    public boolean lua() {
        return RUNTIME_LUA.equals(runtime);
    }
}
