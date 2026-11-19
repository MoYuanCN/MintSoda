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

    public PluginDescriptor {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        runtime = runtime == null || runtime.isBlank()
                ? RUNTIME_JAVA
                : runtime.trim().toUpperCase(java.util.Locale.ROOT);
        entry = entry == null || entry.isBlank() ? "main.js" : entry.trim();
    }

    public boolean compatible() {
        return apiVersion != null && apiVersion <= PluginApi.VERSION;
    }

    /**
     * 是否为脚本插件
     */
    public boolean script() {
        return RUNTIME_JAVASCRIPT.equals(runtime);
    }
}
