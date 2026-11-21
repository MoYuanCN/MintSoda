package mint.soda.service;

import mint.soda.commons.GsonStatic;
import mint.soda.commons.URLUtils;
import mint.soda.entity.Config;
import mint.soda.plugin.PluginInfo;
import mint.soda.plugin.PluginInstaller;
import mint.soda.plugin.PluginManager;
import mint.soda.plugin.PluginMarketIndex;
import mint.soda.plugin.PluginMarketService;
import mint.soda.plugin.PluginPaths;
import mint.soda.plugin.api.PluginPermission;
import mint.soda.util.other.ConfigUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * 插件运行概览。
     */
    public PluginManager.PluginStats stats() {
        return pluginManager.stats();
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

    /**
     * 保存用户为插件授予的权限。
     * <p>
     * 权限变化会影响插件注册的扩展点，因此保存后立即重载插件：已启用的插件按新权限
     * 重新注册，未启用的插件只更新记录。未知权限标识会被丢弃，避免写入脏数据。
     *
     * @param id          插件标识
     * @param permissions 权限标识列表
     * @return 更新后的插件信息
     */
    public PluginInfo setPermissions(String id, List<String> permissions) {
        PluginPaths.verifyId(id);
        Assert.isTrue(pluginManager.find(id).isPresent() || PluginPaths.descriptorFile(id).isFile(), "插件未安装");
        List<String> granted = PluginPermission.sanitize(permissions);
        pluginManager.updateRecord(id, record -> record.setGrantedPermissions(new ArrayList<>(granted)));
        if (pluginManager.registry().get(id).map(record -> Boolean.TRUE.equals(record.getEnable())).orElse(false)) {
            pluginManager.reload(id);
        }
        log.info("插件 {} 权限已更新为 {}", id, granted);
        return info(id);
    }

    /**
     * 可授予的权限清单，供前端渲染开关。
     */
    public List<PluginPermissionView> permissionCatalog() {
        return Arrays.stream(PluginPermission.values())
                .map(permission -> new PluginPermissionView(
                        permission.id(), permission.label(), permission.description(),
                        permission.group().name(), permission.group().label(),
                        permission.risk().name(), permission.enforced()))
                .toList();
    }

    /**
     * 权限项展示模型：分组与风险等级供前端分栏与提示。
     */
    public record PluginPermissionView(String id, String label, String description,
                                       String group, String groupLabel, String risk, boolean enforced) {
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
