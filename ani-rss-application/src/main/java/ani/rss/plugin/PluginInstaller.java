package ani.rss.plugin;

import ani.rss.commons.GsonStatic;
import ani.rss.commons.URLUtils;
import ani.rss.plugin.api.PluginDescriptor;
import ani.rss.util.basic.HttpReq;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.UUID;

/**
 * 插件包的下载、校验与落盘。
 * <p>
 * 安装流程与市场、更新逻辑分开：这里只负责把字节安全地变成插件目录，
 * 是否符合版本、是否启用由 {@link PluginManager} 与调用方决定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginInstaller {

    /**
     * 插件包上限，与前端提示保持一致。
     */
    public static final long MAX_PLUGIN_SIZE = 64L * 1024 * 1024;

    private final PluginManager pluginManager;

    /**
     * 从远程地址下载并安装。
     *
     * @param url    插件 JAR 地址
     * @param sha256 期望的 sha256，为空时不做校验
     * @param size   期望的字节数，为空时不做校验
     * @param source 记录到注册表的来源
     */
    public PluginInfo installFromUrl(String url, String sha256, Long size, String source) {
        URLUtils.verify(url);
        File temp = FileUtil.createTempFile("plugin-", ".jar", true);
        try {
            HttpReq.get(url).then(res -> {
                HttpReq.assertStatus(res);
                FileUtil.writeBytes(res.bodyBytes(), temp);
            });
            return installFromFile(temp, sha256, size, StrUtil.blankToDefault(source, url));
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
     * 安装本地文件：校验大小、sha256 与描述文件后，先写临时目录再替换正式目录。
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

        File staging = new File(FileUtil.getTmpDir(), "plugin-staging-" + UUID.randomUUID());
        PluginPaths.ensure(staging);
        try {
            FileUtil.copy(jar, new File(staging, "plugin.jar"), true);
            FileUtil.writeUtf8String(GsonStatic.toJson(descriptor), new File(staging, "plugin.json"));

            if (pluginManager.find(id).isPresent()) {
                pluginManager.disable(id);
            }
            PluginPaths.ensure(PluginPaths.dir(id));
            FileUtil.move(new File(staging, "plugin.jar"), PluginPaths.jarFile(id), true);
            FileUtil.move(new File(staging, "plugin.json"), PluginPaths.descriptorFile(id), true);
        } finally {
            FileUtil.del(staging);
        }

        pluginManager.updateRecord(id, record -> record
                .setSource(StrUtil.nullToEmpty(source))
                .setInstalledAt(System.currentTimeMillis())
                .setSha256(actualSha256));

        boolean enable = pluginManager.registry().get(id).map(PluginRecord::getEnable).orElse(false);
        if (enable) {
            pluginManager.reload(id);
        } else {
            pluginManager.load(id);
        }
        log.info("插件已安装 {} {}", descriptor.name(), descriptor.version());
        return pluginManager.describe(id);
    }
}
