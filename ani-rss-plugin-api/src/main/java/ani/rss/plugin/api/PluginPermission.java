package ani.rss.plugin.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 插件可申请的权限。
 * <p>
 * 权限按能力分组，用户在插件页逐项开关；未授予的扩展点在注册阶段会被跳过。
 * 读写分开授权：只读能力（{@code *_READ}）不能写数据，写能力（{@code *_WRITE}）不隐含读取。
 * <p>
 * {@link #enforced()} 为 {@code false} 的是敏感但无法技术限制的能力：Java 插件与服务同进程，
 * 脚本插件也没有网络与文件 API，这类权限只用于展示与审计。
 */
public enum PluginPermission {

    // ---- 订阅 ----

    ITEM_FILTER("ITEM_FILTER", "过滤条目", "在匹配规则之后接受或拒绝单条资源", Group.SUBSCRIPTION, Risk.MEDIUM, true),
    SUBSCRIPTION_EVENTS("SUBSCRIPTION_EVENTS", "订阅事件", "接收订阅创建、修改与删除的通知及其元数据", Group.SUBSCRIPTION, Risk.LOW, true,
            "SUBSCRIPTION_LISTENER", "SUBSCRIPTION_EVENT"),
    TEMPLATE_VARIABLE("TEMPLATE_VARIABLE", "模板变量", "向通知、重命名与下载路径模板追加占位符", Group.SUBSCRIPTION, Risk.LOW, true,
            "TEMPLATE_VARIABLES"),

    // ---- 下载与通知 ----

    NOTIFICATION("NOTIFICATION", "发送通知", "注册通知渠道，向外部地址投递订阅更新", Group.DOWNLOAD, Risk.MEDIUM, true),
    DOWNLOAD_INTERCEPT("DOWNLOAD_INTERCEPT", "下载拦截", "在提交下载器之前修改保存位置或名称", Group.DOWNLOAD, Risk.HIGH, true,
            "DOWNLOAD_INTERCEPTOR", "INTERCEPTOR"),
    DOWNLOAD_CANCEL("DOWNLOAD_CANCEL", "取消下载", "直接取消匹配到的下载任务", Group.DOWNLOAD, Risk.HIGH, true),

    // ---- 任务 ----

    SCHEDULED_TASK("SCHEDULED_TASK", "周期任务", "按固定间隔在后台运行插件逻辑", Group.TASK, Risk.LOW, true,
            "TASK", "SCHEDULE"),

    // ---- 接口 ----

    HTTP_ENDPOINT("HTTP_ENDPOINT", "自定义接口", "对外提供 /api/plugin/api/{id}/** 接口（仍需登录）", Group.API, Risk.MEDIUM, true,
            "ENDPOINT"),

    // ---- 配置与数据 ----

    CONFIG_READ("CONFIG_READ", "读取配置", "读取插件页保存的设置项", Group.STORAGE, Risk.LOW, true),
    CONFIG_WRITE("CONFIG_WRITE", "写入配置", "保存插件页设置项", Group.STORAGE, Risk.MEDIUM, true),
    DATA_READ("DATA_READ", "读取数据", "读取插件私有目录 data/ 下的文件", Group.STORAGE, Risk.LOW, true),
    DATA_WRITE("DATA_WRITE", "写入数据", "在插件私有目录 data/ 下写入、覆盖与删除文件", Group.STORAGE, Risk.HIGH, true),

    // ---- 系统 ----

    LOG("LOG", "写日志", "向应用日志输出带插件标识的消息", Group.SYSTEM, Risk.LOW, true),

    /**
     * 出站网络请求，仅 Java 插件可用，无法强制限制
     */
    NETWORK("NETWORK", "出站网络", "发起网络请求；Java 插件与服务同进程，无法技术限制", Group.SYSTEM, Risk.HIGH, false),

    /**
     * 文件系统访问，仅 Java 插件可用，无法强制限制
     */
    FILE_SYSTEM("FILE_SYSTEM", "文件系统", "读写应用目录外的文件；Java 插件无法技术限制", Group.SYSTEM, Risk.HIGH, false);

    /**
     * 权限分组，供设置界面分栏展示
     */
    public enum Group {
        SUBSCRIPTION("订阅"), DOWNLOAD("下载与通知"), TASK("任务"), API("接口"), STORAGE("配置与数据"), SYSTEM("系统");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * 风险等级，用于提示用户谨慎授权
     */
    public enum Risk {
        LOW("低"), MEDIUM("中"), HIGH("高");

        private final String label;

        Risk(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * 历史标识到新标识的映射；一个旧标识可能展开为多项。
     */
    private static final java.util.Map<String, List<String>> LEGACY = java.util.Map.of(
            "CONFIG", List.of("CONFIG_READ", "CONFIG_WRITE"),
            "DATA_DIR", List.of("DATA_READ", "DATA_WRITE"),
            "DATA", List.of("DATA_READ", "DATA_WRITE")
    );

    private final String id;
    private final String label;
    private final String description;
    private final Group group;
    private final Risk risk;
    private final boolean enforced;
    private final String[] aliases;

    PluginPermission(String id, String label, String description, Group group, Risk risk, boolean enforced, String... aliases) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.group = group;
        this.risk = risk;
        this.enforced = enforced;
        this.aliases = aliases == null ? new String[0] : aliases;
    }

    /**
     * 权限标识，写入插件描述与注册表
     */
    public String id() {
        return id;
    }

    /**
     * 显示名称
     */
    public String label() {
        return label;
    }

    /**
     * 说明
     */
    public String description() {
        return description;
    }

    /**
     * 分组
     */
    public Group group() {
        return group;
    }

    /**
     * 风险等级
     */
    public Risk risk() {
        return risk;
    }

    /**
     * 是否由宿主强制校验
     */
    public boolean enforced() {
        return enforced;
    }

    /**
     * 按标识查找，仅匹配唯一标准标识；历史上展开为多项的旧标识返回空。
     */
    public static Optional<PluginPermission> of(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = normalize(id);
        return Arrays.stream(values())
                .filter(permission -> permission.matches(normalized))
                .findFirst();
    }

    /**
     * 把标识解析为一项或多项权限。
     * <p>
     * 旧标识 {@code CONFIG}、{@code DATA_DIR} 会展开为读与写两项；未知标识返回空列表。
     *
     * @param id 权限标识，大小写与分隔符不敏感
     * @return 解析后的权限列表
     */
    public static List<PluginPermission> resolve(String id) {
        if (id == null || id.isBlank()) {
            return List.of();
        }
        String normalized = normalize(id);
        List<String> expanded = LEGACY.get(normalized);
        if (expanded != null) {
            List<PluginPermission> result = new ArrayList<>();
            for (String target : expanded) {
                Arrays.stream(values())
                        .filter(permission -> permission.id.equals(target))
                        .findFirst()
                        .ifPresent(result::add);
            }
            return result;
        }
        return of(normalized).map(List::of).orElse(List.of());
    }

    /**
     * 统一大小写与分隔符，供描述文件解析与前端回填共用。
     */
    public static String normalize(String id) {
        if (id == null) {
            return "";
        }
        return id.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * 过滤未知标识，并把旧标识展开为标准标识，保持顺序去重。
     */
    public static List<String> sanitize(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String id : ids) {
            resolve(id).stream().map(PluginPermission::id).forEach(result::add);
        }
        return List.copyOf(result);
    }

    /**
     * 判断一组已授予权限是否包含所需权限。
     *
     * @param granted 已授予的标识
     * @param needed  需要的权限
     */
    public static boolean granted(List<String> granted, PluginPermission needed) {
        if (needed == null) {
            return false;
        }
        return sanitize(granted).stream().anyMatch(needed.id()::equals);
    }

    /**
     * 全部已知权限标识
     */
    public static List<String> all() {
        return Arrays.stream(values()).map(PluginPermission::id).toList();
    }

    private boolean matches(String normalized) {
        if (id.equals(normalized)) {
            return true;
        }
        return Arrays.stream(aliases).anyMatch(alias -> Objects.equals(normalize(alias), normalized));
    }
}
