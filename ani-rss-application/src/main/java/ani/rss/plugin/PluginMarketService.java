package ani.rss.plugin;

import ani.rss.commons.GsonStatic;
import ani.rss.commons.URLUtils;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 插件市场索引的获取与条目解析。
 * <p>
 * 市场地址优先使用调用方传入的值，其次使用配置中的 {@code pluginMarketUrl}；
 * 两种来源都经过 {@link URLUtils#verify} 校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginMarketService {

    private final PluginManager pluginManager;

    /**
     * 获取市场索引。
     *
     * @param url 市场地址，可为空
     */
    public PluginMarketIndex index(String url) {
        String marketUrl = resolveUrl(url);
        Assert.notBlank(marketUrl, "未配置插件市场地址");
        URLUtils.verify(marketUrl);
        String body = HttpReq.get(marketUrl).execute().body();
        return GsonStatic.fromJson(body, PluginMarketIndex.class);
    }

    /**
     * 在市场中查找插件条目，找不到或市场不可用时返回空。
     */
    public Optional<PluginMarketIndex.MarketEntry> entry(String id, String url) {
        try {
            return index(url).plugins().stream()
                    .filter(item -> id.equals(item.id()))
                    .findFirst();
        } catch (Exception e) {
            log.warn("插件市场查询失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 解析插件自身的更新条目，地址来自插件描述文件的 {@code updateUrl}。
     */
    public Optional<PluginMarketIndex.MarketEntry> selfEntry(String updateUrl) {
        if (StrUtil.isBlank(updateUrl)) {
            return Optional.empty();
        }
        URLUtils.verify(updateUrl);
        String body = HttpReq.get(updateUrl).execute().body();
        return Optional.ofNullable(GsonStatic.fromJson(body, PluginMarketIndex.MarketEntry.class));
    }

    /**
     * 解析下载地址：市场条目优先，其次插件自带的更新地址。
     */
    public String downloadUrl(String id, String marketUrl) {
        Optional<PluginMarketIndex.MarketEntry> entry = entry(id, marketUrl);
        if (entry.isPresent() && StrUtil.isNotBlank(entry.get().downloadUrl())) {
            return entry.get().downloadUrl();
        }
        return selfEntry(pluginManager.describe(id).updateUrl())
                .map(PluginMarketIndex.MarketEntry::downloadUrl)
                .orElse("");
    }

    public String resolveUrl(String url) {
        return StrUtil.blankToDefault(StrUtil.trim(url), StrUtil.trim(ConfigUtil.CONFIG.getPluginMarketUrl()));
    }
}
