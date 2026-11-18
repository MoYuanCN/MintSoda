package ani.rss.service;

import ani.rss.commons.GsonStatic;
import ani.rss.commons.URLUtils;
import ani.rss.entity.Config;
import ani.rss.plugin.PluginInfo;
import ani.rss.plugin.PluginInstaller;
import ani.rss.plugin.PluginManager;
import ani.rss.plugin.PluginMarketIndex;
import ani.rss.plugin.PluginMarketService;
import ani.rss.plugin.PluginPaths;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * 插件市场、安装、更新与配置。
 * <p>
 * 下载与校验在 {@link PluginInstaller}，市场访问在 {@link PluginMarketService}；
 * 这里只做流程编排，保持每个入口可单独阅读。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginService {

    private final PluginManager pluginManager;
    private final PluginInstaller pluginInstaller;
    private final PluginMarketService pluginMarketService;

    public List<PluginInfo> list() {
        return pluginManager.list();
    }

    public PluginInfo info(String id) {
        PluginPaths.verifyId(id);
        return pluginManager.describe(id);
    }

    public void enable(String id) {
        Assert.isTrue(pluginManager.find(id).isPresent() || PluginPaths.jarFile(id).isFile(), "插件未安装");
        pluginManager.enable(id);
    }

    public void disable(String id) {
        pluginManager.disable(id);
    }

    public void uninstall(String id) {
        pluginManager.uninstall(id);
    }

    public void reload(String id) {
        pluginManager.reload(id);
    }

    public void saveConfig(String id, String config) {
        PluginPaths.verifyId(id);
        if (config != null && !config.isBlank()) {
            // 插件配置必须是合法 JSON，避免前端写入任意内容后再次解析失败。
            try {
                GsonStatic.GSON.fromJson(config, com.google.gson.JsonElement.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("插件配置必须是合法 JSON");
            }
        }
        pluginManager.updateRecord(id, record -> record.setConfig(StrUtil.nullToEmpty(config)));
    }

    public PluginMarketIndex market(String url) {
        return pluginMarketService.index(url);
    }

    public PluginInfo installFromMarket(PluginMarketIndex.MarketEntry entry) {
        Assert.notNull(entry, "插件条目不能为空");
        Assert.notBlank(entry.downloadUrl(), "插件下载地址不能为空");
        return pluginInstaller.installFromUrl(entry.downloadUrl(), entry.sha256(), entry.size(), "market");
    }

    public PluginInfo installFromUrl(String url, String sha256, Long size) {
        return pluginInstaller.installFromUrl(url, sha256, size, "url");
    }

    public PluginInfo installFromUpload(MultipartFile file, String sha256) throws Exception {
        return pluginInstaller.installFromUpload(file, sha256);
    }

    /**
     * 检查插件更新。优先使用市场索引，其次使用插件自身的 updateUrl。
     *
     * @param id        插件标识
     * @param marketUrl 市场地址，可为空
     */
    public PluginInfo checkUpdate(String id, String marketUrl) {
        PluginInfo current = info(id);
        Optional<PluginMarketIndex.MarketEntry> marketEntry = pluginMarketService.entry(id, marketUrl);
        Optional<PluginMarketIndex.MarketEntry> updateEntry = marketEntry.isPresent()
                ? marketEntry
                : pluginMarketService.selfEntry(current.updateUrl());

        String latest = updateEntry.map(entry -> StrUtil.nullToEmpty(entry.version())).orElse("");
        boolean available = StrUtil.isNotBlank(latest) && !latest.equals(current.version());

        pluginManager.updateRecord(id, record -> record
                .setLatestVersion(latest)
                .setUpdateAvailable(available)
                .setLastCheckTime(System.currentTimeMillis()));
        if (available) {
            log.info("插件 {} 有新版本 {} -> {}", id, current.version(), latest);
        }
        return info(id);
    }

    /**
     * 安装检查到的更新。
     */
    public PluginInfo update(String id, String marketUrl) {
        PluginInfo current = checkUpdate(id, marketUrl);
        if (!Boolean.TRUE.equals(current.updateAvailable())) {
            return current;
        }
        String downloadUrl = pluginMarketService.downloadUrl(id, marketUrl);
        Assert.notBlank(downloadUrl, "未找到插件下载地址");
        PluginMarketIndex.MarketEntry entry = pluginMarketService.entry(id, marketUrl).orElse(null);
        pluginInstaller.installFromUrl(downloadUrl,
                entry == null ? null : entry.sha256(),
                entry == null ? null : entry.size(),
                "update");
        return info(id);
    }

    /**
     * 检查所有插件更新，失败逐条忽略。
     */
    public List<PluginInfo> checkAll(String marketUrl) {
        for (PluginInfo info : list()) {
            try {
                checkUpdate(info.id(), marketUrl);
            } catch (Exception e) {
                log.warn("插件 {} 更新检查失败: {}", info.id(), e.getMessage());
            }
        }
        return list();
    }

    public String marketUrl() {
        return StrUtil.nullToEmpty(ConfigUtil.CONFIG.getPluginMarketUrl());
    }

    public void setMarketUrl(String url) {
        Config config = ConfigUtil.CONFIG;
        String value = StrUtil.trimToEmpty(url);
        if (StrUtil.isNotBlank(value)) {
            URLUtils.verify(value);
        }
        config.setPluginMarketUrl(value);
        ConfigUtil.sync();
    }
}
