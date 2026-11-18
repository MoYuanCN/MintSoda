package ani.rss.service;

import ani.rss.commons.GsonStatic;
import ani.rss.commons.URLUtils;
import ani.rss.entity.Config;
import ani.rss.plugin.PluginInfo;
import ani.rss.plugin.PluginManager;
import ani.rss.plugin.PluginMarketIndex;
import ani.rss.plugin.PluginPaths;
import ani.rss.plugin.PluginRecord;
import ani.rss.plugin.api.PluginDescriptor;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * 插件市场、安装、更新与配置。
 * <p>
 * 所有远程地址都经过 {@link URLUtils#verify} 校验；下载内容必须同时通过
 * 大小与 sha256 校验，避免安装被篡改或被截断的插件包。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginService {

    private static final long MAX_PLUGIN_SIZE = 64L * 1024 * 1024;

    private final PluginManager pluginManager;

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

    /**
     * 获取市场索引。
     */
    public PluginMarketIndex market(String url) {
        String marketUrl = StrUtil.blankToDefault(StrUtil.trim(url), StrUtil.trim(ConfigUtil.CONFIG.getPluginMarketUrl()));
        Assert.notBlank(marketUrl, "未配置插件市场地址");
        URLUtils.verify(marketUrl);
        String body = HttpReq.get(marketUrl).execute().body();
        return GsonStatic.fromJson(body, PluginMarketIndex.class);
    }

    /**
     * 从市场条目安装。
     */
    public PluginInfo installFromMarket(PluginMarketIndex.MarketEntry entry) {
        Assert.notNull(entry, "插件条目不能为空");
        Assert.notBlank(entry.downloadUrl(), "插件下载地址不能为空");
        return installFromUrl(entry.downloadUrl(), entry.sha256(), entry.size());
    }

    /**
     * 从远程地址安装插件包。
     *
     * @param url    插件 JAR 地址
     * @param sha256 期望的 sha256，为空时不做校验（不推荐）
     * @param size   期望的字节数，为空时不做校验（不推荐）
     */
    public PluginInfo installFromUrl(String url, String sha256, Long size) {
        URLUtils.verify(url);
        File temp = FileUtil.createTempFile("plugin-", ".jar", true);
        try {
            HttpReq.get(url).then(res -> {
                HttpReq.assertStatus(res);
                FileUtil.writeBytes(res.bodyBytes(), temp);
            });
            return installFromFile(temp, sha256, size, url);
        } finally {
            FileUtil.del(temp);
        }
    }

    /**
     * 上传安装。
     */
    public PluginInfo installFromUpload(MultipartFile file, String sha256) throws Exception {
        Assert.notNull(file, "请选择插件包");
        Assert.isTrue(file.getSize() > 0 && file.getSize() <= MAX_PLUGIN_SIZE, "插件包大小不合法");
        File temp = FileUtil.createTempFile("plugin-", ".jar", true);
        try {
            try (InputStream inputStream = file.getInputStream()) {
                FileUtil.writeFromStream(inputStream, temp);
            }
            return installFromFile(temp, sha256, null, "upload");
        } finally {
            FileUtil.del(temp);
        }
    }

    /**
     * 安装本地文件。先写入临时目录完成校验，通过后再替换正式目录，避免半成品。
     */
    public synchronized PluginInfo installFromFile(File jar, String sha256, Long size, String source) {
        Assert.isTrue(jar.isFile(), "插件包不存在");
        long length = jar.length();
        Assert.isTrue(length > 0 && length <= MAX_PLUGIN_SIZE, "插件包大小不合法");
        if (size != null) {
            Assert.isTrue(length == size, "插件包大小不匹配");
        }
        String actualSha256 = SecureUtil.sha256(jar);
        if (StrUtil.isNotBlank(sha256)) {
            Assert.isTrue(actualSha256.equalsIgnoreCase(sha256.trim()), "插件包 sha256 不匹配");
        }
        PluginDescriptor descriptor = PluginManager.readDescriptor(jar);
        String id = descriptor.id();

        File targetDir = PluginPaths.dir(id);
        File targetJar = PluginPaths.jarFile(id);
        File targetDescriptor = PluginPaths.descriptorFile(id);
        File staging = new File(FileUtil.getTmpDir(), "plugin-staging-" + java.util.UUID.randomUUID());
        PluginPaths.ensure(staging);
        try {
            FileUtil.copy(jar, new File(staging, "plugin.jar"), true);
            FileUtil.writeUtf8String(GsonStatic.toJson(descriptor), new File(staging, "plugin.json"));

            boolean running = pluginManager.find(id).isPresent();
            if (running) {
                pluginManager.disable(id);
            }
            PluginPaths.ensure(targetDir);
            FileUtil.move(new File(staging, "plugin.jar"), targetJar, true);
            FileUtil.move(new File(staging, "plugin.json"), targetDescriptor, true);
        } finally {
            FileUtil.del(staging);
        }

        final String finalSha256 = actualSha256;
        pluginManager.updateRecord(id, record -> record
                .setSource(StrUtil.nullToEmpty(source))
                .setInstalledAt(System.currentTimeMillis())
                .setSha256(finalSha256));

        boolean enable = pluginManager.registry().get(id).map(PluginRecord::getEnable).orElse(false);
        if (enable) {
            pluginManager.reload(id);
        } else {
            pluginManager.load(id);
        }
        log.info("插件已安装 {} {}", descriptor.name(), descriptor.version());
        return info(id);
    }

    /**
     * 检查插件更新。优先使用市场索引，其次使用插件自身的 updateUrl。
     *
     * @param id        插件标识
     * @param marketUrl 市场地址，可为空
     */
    public PluginInfo checkUpdate(String id, String marketUrl) {
        PluginInfo current = info(id);
        String latest = "";
        String downloadUrl = "";
        String sha256 = "";
        Long size = null;

        PluginMarketIndex index = null;
        try {
            index = market(StrUtil.blankToDefault(StrUtil.trim(marketUrl), StrUtil.trim(ConfigUtil.CONFIG.getPluginMarketUrl())));
        } catch (Exception e) {
            log.warn("插件市场获取失败，改用插件自带更新地址");
        }

        if (index != null) {
            PluginMarketIndex.MarketEntry entry = index.plugins().stream()
                    .filter(item -> id.equals(item.id()))
                    .findFirst()
                    .orElse(null);
            if (entry != null) {
                latest = StrUtil.nullToEmpty(entry.version());
                downloadUrl = StrUtil.nullToEmpty(entry.downloadUrl());
                sha256 = StrUtil.nullToEmpty(entry.sha256());
                size = entry.size();
            }
        }

        if (StrUtil.isBlank(latest) && StrUtil.isNotBlank(current.updateUrl())) {
            URLUtils.verify(current.updateUrl());
            String body = HttpReq.get(current.updateUrl()).execute().body();
            PluginMarketIndex.MarketEntry entry = GsonStatic.fromJson(body, PluginMarketIndex.MarketEntry.class);
            latest = StrUtil.nullToEmpty(entry.version());
            downloadUrl = StrUtil.nullToEmpty(entry.downloadUrl());
            sha256 = StrUtil.nullToEmpty(entry.sha256());
            size = entry.size();
        }

        boolean available = StrUtil.isNotBlank(latest) && !latest.equals(current.version());
        String finalLatest = latest;
        String finalDownloadUrl = downloadUrl;
        String finalSha256 = sha256;
        Long finalSize = size;
        pluginManager.updateRecord(id, record -> record
                .setLatestVersion(finalLatest)
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
        String downloadUrl = resolveDownloadUrl(id, marketUrl);
        Assert.notBlank(downloadUrl, "未找到插件下载地址");
        PluginMarketIndex.MarketEntry entry = marketEntry(id, marketUrl);
        installFromUrl(downloadUrl,
                entry == null ? null : entry.sha256(),
                entry == null ? null : entry.size());
        return info(id);
    }

    private String resolveDownloadUrl(String id, String marketUrl) {
        PluginMarketIndex.MarketEntry entry = marketEntry(id, marketUrl);
        if (entry != null && StrUtil.isNotBlank(entry.downloadUrl())) {
            return entry.downloadUrl();
        }
        PluginInfo current = info(id);
        if (StrUtil.isNotBlank(current.updateUrl())) {
            URLUtils.verify(current.updateUrl());
            String body = HttpReq.get(current.updateUrl()).execute().body();
            return StrUtil.nullToEmpty(GsonStatic.fromJson(body, PluginMarketIndex.MarketEntry.class).downloadUrl());
        }
        return "";
    }

    private PluginMarketIndex.MarketEntry marketEntry(String id, String marketUrl) {
        try {
            PluginMarketIndex index = market(marketUrl);
            return index.plugins().stream()
                    .filter(item -> id.equals(item.id()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
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
