package mint.soda.plugin;

import java.util.List;

/**
 * 插件市场索引，来自可配置的远程 JSON。
 *
 * @param name     市场名称
 * @param updatedAt 更新时间
 * @param plugins  插件列表
 */
public record PluginMarketIndex(String name, String updatedAt, List<MarketEntry> plugins) {

    public PluginMarketIndex {
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
    }

    /**
     * 市场条目
     *
     * @param id          插件标识
     * @param name        名称
     * @param version     版本
     * @param description 简介
     * @param author      作者
     * @param homepage    主页
     * @param downloadUrl 插件包下载地址
     * @param sha256      插件包 sha256，用于安装校验
     * @param size        插件包字节数，用于安装校验
     * @param apiVersion  接口版本
     * @param permissions 声明能力
     * @param updateUrl   该插件自己的更新检查地址
     */
    public record MarketEntry(
            String id,
            String name,
            String version,
            String description,
            String author,
            String homepage,
            String downloadUrl,
            String sha256,
            Long size,
            Integer apiVersion,
            List<String> permissions,
            String updateUrl
    ) {
        public MarketEntry {
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }
}
