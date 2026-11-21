package mint.soda.plugin;

import mint.soda.plugin.api.PluginDescriptor;

import java.util.List;

/**
 * 插件列表项，供 WebUI 展示。
 *
 * @param id            插件标识
 * @param name          名称
 * @param version       已安装版本
 * @param apiVersion    插件接口版本
 * @param author        作者
 * @param description   简介
 * @param homepage      主页
 * @param permissions   插件声明的权限
 * @param state         运行状态
 * @param error         错误信息，正常为空
 * @param source        安装来源
 * @param installedAt   安装时间戳
 * @param updateUrl     自动更新检查地址
 * @param latestVersion 市场或更新检查得到的最新版本
 * @param updateAvailable 是否有可用更新
 * @param sections      设置页区块
 * @param config        插件配置 JSON
 * @param channels      提供的通知渠道
 * @param endpoints     提供的 HTTP 接口
 * @param pages         提供的界面页面
 * @param runtime       运行时：JAVA / JAVASCRIPT / LUA
 * @param grantedPermissions 用户授予的权限；为空表示尚未单独配置
 * @param subscribedTopics   订阅的联动主题
 * @param servedTopics       应答的联动主题
 */
public record PluginInfo(
        String id,
        String name,
        String version,
        Integer apiVersion,
        String author,
        String description,
        String homepage,
        List<String> permissions,
        PluginState state,
        String error,
        String source,
        Long installedAt,
        String updateUrl,
        String latestVersion,
        Boolean updateAvailable,
        List<SectionView> sections,
        String config,
        List<ChannelView> channels,
        List<EndpointView> endpoints,
        List<PageView> pages,
        String runtime,
        List<String> grantedPermissions,
        List<String> subscribedTopics,
        List<String> servedTopics
) {
    public PluginInfo {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        sections = sections == null ? List.of() : List.copyOf(sections);
        channels = channels == null ? List.of() : List.copyOf(channels);
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        pages = pages == null ? List.of() : List.copyOf(pages);
        grantedPermissions = grantedPermissions == null ? List.of() : List.copyOf(grantedPermissions);
        subscribedTopics = subscribedTopics == null ? List.of() : List.copyOf(subscribedTopics);
        servedTopics = servedTopics == null ? List.of() : List.copyOf(servedTopics);
    }

    public static PluginInfo from(PluginDescriptor descriptor) {
        return new PluginInfo(
                descriptor.id(), descriptor.name(), descriptor.version(), descriptor.apiVersion(),
                descriptor.author(), descriptor.description(), descriptor.homepage(),
                descriptor.permissions(), PluginState.DISABLED, "", "", 0L,
                descriptor.updateUrl(), "", false, List.of(), "", List.of(), List.of(), List.of(),
                descriptor.runtime(), List.of(), List.of(), List.of()
        );
    }

    public record SectionView(String id, String title, String description, List<FieldView> fields) {
    }

    public record FieldView(String key, String label, String type, String placeholder,
                            boolean required, List<String> options) {
    }

    public record ChannelView(String id, String name, List<FieldView> fields) {
    }

    public record EndpointView(String path, List<String> methods) {
        public EndpointView {
            methods = methods == null ? List.of() : List.copyOf(methods);
        }
    }

    /**
     * 插件页面的展示模型。区块类型与字段沿用设置页的白名单结构，
     * 前端不会把插件返回值当作 HTML 渲染。
     */
    public record PageView(String id, String title, String icon, String description, List<BlockView> blocks) {
        public PageView {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    public record BlockView(String type, String title, String text, List<FieldView> fields,
                            String source, List<ColumnView> columns, List<ActionView> actions,
                            List<StatView> stats) {
        public BlockView {
            fields = fields == null ? List.of() : List.copyOf(fields);
            columns = columns == null ? List.of() : List.copyOf(columns);
            actions = actions == null ? List.of() : List.copyOf(actions);
            stats = stats == null ? List.of() : List.copyOf(stats);
        }
    }

    public record StatView(String label, String value, String hint) {
    }

    public record ColumnView(String key, String label) {
    }

    public record ActionView(String id, String label, String source, String method,
                             boolean confirm, String confirmText) {
    }
}
