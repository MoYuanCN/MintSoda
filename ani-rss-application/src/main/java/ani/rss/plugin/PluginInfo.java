package ani.rss.plugin;

import ani.rss.plugin.api.PluginDescriptor;

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
 * @param permissions   声明能力
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
        List<ChannelView> channels
) {
    public PluginInfo {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        sections = sections == null ? List.of() : List.copyOf(sections);
        channels = channels == null ? List.of() : List.copyOf(channels);
    }

    public static PluginInfo from(PluginDescriptor descriptor) {
        return new PluginInfo(
                descriptor.id(), descriptor.name(), descriptor.version(), descriptor.apiVersion(),
                descriptor.author(), descriptor.description(), descriptor.homepage(),
                descriptor.permissions(), PluginState.DISABLED, "", "", 0L,
                descriptor.updateUrl(), "", false, List.of(), "", List.of()
        );
    }

    public record SectionView(String id, String title, String description, List<FieldView> fields) {
    }

    public record FieldView(String key, String label, String type, String placeholder,
                            boolean required, List<String> options) {
    }

    public record ChannelView(String id, String name, List<FieldView> fields) {
    }
}
