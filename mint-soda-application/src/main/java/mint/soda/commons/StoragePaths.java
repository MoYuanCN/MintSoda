package mint.soda.commons;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 运行时存储布局。
 * <p>
 * 配置目录只保留设置文件，其余数据统一放在配置目录同级的 {@code data/}：
 * <pre>
 * root/
 *   config/config.json
 *   data/subscriptions.json
 *   data/images/covers/     订阅封面缓存
 *   data/images/previews/   站点预览图缓存
 *   data/images/assets/     用户上传的外观图
 *   data/torrents/          种子缓存
 *   data/plugins/           插件与私有数据
 *   data/webui/             在线下载的 WebUI
 *   data/backups/           自动备份
 *   data/logs/              运行日志
 * </pre>
 * 配置目录不叫 {@code config}（自定义 CONFIG 目录）时，{@code data/} 放在该目录内部。
 */
public final class StoragePaths {

    public static final String CONFIG_FILE_NAME = "config.json";
    public static final String SUBSCRIPTIONS_FILE_NAME = "subscriptions.json";

    private static final String CONFIG_DIR_NAME = "config";
    private static final String DATA_DIR_NAME = "data";

    /** 旧版直接放在配置目录内的数据子目录，迁移时映射到新位置。 */
    private static final List<String> LEGACY_DIRS = List.of(
            "files", "img", "ui-assets", "torrents", "plugins", "webui", "backup", "logs");

    private static volatile File boundConfigDir;

    private StoragePaths() {
    }

    /**
     * 绑定配置目录，首次绑定时执行旧版布局迁移。
     *
     * @param configDir 配置目录
     */
    public static synchronized void bind(File configDir) {
        File target = configDir.getAbsoluteFile();
        if (target.equals(boundConfigDir)) {
            return;
        }
        boolean first = boundConfigDir == null;
        boundConfigDir = target;
        if (first) {
            migrateLegacyLayout();
        }
        ensureLayout();
    }

    public static File configDir() {
        File dir = boundConfigDir;
        if (dir == null) {
            throw new IllegalStateException("存储目录尚未初始化");
        }
        return dir;
    }

    public static File configFile() {
        return new File(configDir(), CONFIG_FILE_NAME);
    }

    public static File dataDir() {
        File dir = configDir();
        File parent = dir.getParentFile();
        if (parent != null && CONFIG_DIR_NAME.equalsIgnoreCase(dir.getName())) {
            return new File(parent, DATA_DIR_NAME);
        }
        return new File(dir, DATA_DIR_NAME);
    }

    public static File imagesDir() {
        return new File(dataDir(), "images");
    }

    public static File coversDir() {
        return new File(imagesDir(), "covers");
    }

    public static File previewsDir() {
        return new File(imagesDir(), "previews");
    }

    public static File assetsDir() {
        return new File(imagesDir(), "assets");
    }

    public static File torrentsDir() {
        return new File(dataDir(), "torrents");
    }

    public static File pluginsDir() {
        return new File(dataDir(), "plugins");
    }

    public static File webuiDir() {
        return new File(dataDir(), "webui");
    }

    public static File backupsDir() {
        return new File(dataDir(), "backups");
    }

    public static File logsDir() {
        return new File(dataDir(), "logs");
    }

    public static File subscriptionsFile() {
        return new File(dataDir(), SUBSCRIPTIONS_FILE_NAME);
    }

    /**
     * 创建基础目录。
     */
    public static void ensureLayout() {
        for (File dir : List.of(configDir(), imagesDir(), coversDir(), previewsDir(), assetsDir(),
                torrentsDir(), pluginsDir(), backupsDir(), logsDir())) {
            FileUtil.mkdir(dir);
        }
    }

    /**
     * 把相对路径安全地解析到根目录内。
     *
     * @param root     根目录
     * @param relative 相对路径
     * @return 解析后的文件；越界时返回 null
     */
    public static File resolveInside(File root, String relative) {
        File base = root.getAbsoluteFile();
        try {
            var basePath = base.toPath().normalize();
            var target = basePath.resolve(StrUtil.nullToEmpty(relative).trim()).normalize();
            if (!target.startsWith(basePath)) {
                return null;
            }
            return target.toFile();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 数据根目录在备份压缩包中的前缀。
     * <p>
     * 备份条目统一以 {@code data/} 开头，与 {@link #resolveImportEntry(String)} 的解析规则一一对应。
     *
     * @param root 数据根目录或文件
     * @return 压缩包前缀；不认识时返回 null
     */
    public static String archivePrefix(File root) {
        File target = root.getAbsoluteFile();
        if (target.equals(coversDir().getAbsoluteFile())) return "data/images/covers";
        if (target.equals(previewsDir().getAbsoluteFile())) return "data/images/previews";
        if (target.equals(assetsDir().getAbsoluteFile())) return "data/images/assets";
        if (target.equals(torrentsDir().getAbsoluteFile())) return "data/torrents";
        if (target.equals(pluginsDir().getAbsoluteFile())) return "data/plugins";
        if (target.equals(webuiDir().getAbsoluteFile())) return "data/webui";
        if (target.equals(subscriptionsFile().getAbsoluteFile())) return "data/" + SUBSCRIPTIONS_FILE_NAME;
        if (target.equals(configFile().getAbsoluteFile())) return CONFIG_FILE_NAME;
        return null;
    }

    /**
     * 把备份条目映射到当前布局的落盘位置。
     * <p>
     * 同时兼容新版 {@code data/...} 与旧版 {@code files/}、{@code img/}、{@code ui-assets/}、
     * {@code plugins/} 等前缀；日志、备份与未知条目返回 null，由调用方跳过。
     *
     * @param entryName 压缩包条目名
     * @return 目标文件，跳过时返回 null
     */
    public static File resolveImportEntry(String entryName) {
        String name = StrUtil.nullToEmpty(entryName).replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.isEmpty() || name.endsWith("/")) {
            return null;
        }
        if (name.startsWith(DATA_DIR_NAME + "/")) {
            return resolveInside(dataDir(), name.substring(DATA_DIR_NAME.length() + 1));
        }
        if (CONFIG_FILE_NAME.equals(name) || "config.v2.json".equals(name)) {
            return configFile();
        }
        if (SUBSCRIPTIONS_FILE_NAME.equals(name) || "ani.v2.json".equals(name)) {
            return subscriptionsFile();
        }
        if (name.startsWith("files/")) {
            return resolveInside(coversDir(), name.substring("files/".length()));
        }
        if (name.startsWith("img/")) {
            return resolveInside(previewsDir(), name.substring("img/".length()));
        }
        if (name.startsWith("ui-assets/")) {
            return resolveInside(assetsDir(), name.substring("ui-assets/".length()));
        }
        if (name.startsWith("plugins/")) {
            return resolveInside(pluginsDir(), name.substring("plugins/".length()));
        }
        if (name.startsWith("torrents/")) {
            return resolveInside(torrentsDir(), name.substring("torrents/".length()));
        }
        if (name.startsWith("webui/")) {
            return resolveInside(webuiDir(), name.substring("webui/".length()));
        }
        return null;
    }

    /**
     * 旧版布局迁移：配置目录内的数据子目录搬到 {@code data/}，旧文件名改为新文件名。
     * <p>
     * 只在目标不存在时移动，重复执行不会覆盖已有数据。
     */
    private static void migrateLegacyLayout() {
        File configDir = boundConfigDir;
        File dataDir = dataDir();

        for (String name : LEGACY_DIRS) {
            mergeMove(new File(configDir, name), legacyDirTarget(name));
        }
        moveFileIfAbsent(new File(configDir, "config.v2.json"), new File(configDir, CONFIG_FILE_NAME));
        moveFileIfAbsent(new File(configDir, "ani.v2.json"), new File(dataDir, SUBSCRIPTIONS_FILE_NAME));
    }

    private static File legacyDirTarget(String name) {
        return switch (name) {
            case "files" -> coversDir();
            case "img" -> previewsDir();
            case "ui-assets" -> assetsDir();
            case "torrents" -> torrentsDir();
            case "plugins" -> pluginsDir();
            case "webui" -> webuiDir();
            case "backup" -> backupsDir();
            case "logs" -> logsDir();
            default -> new File(dataDir(), name);
        };
    }

    private static void moveFileIfAbsent(File source, File target) {
        if (!source.isFile() || target.exists()) {
            return;
        }
        try {
            FileUtil.mkdir(target.getParentFile());
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 跨盘移动会失败，退化为复制后删除源文件。
            FileUtil.copy(source, target, true);
            FileUtil.del(source);
        }
    }

    /**
     * 目录迁移：目标不存在时整体移动，已存在时逐项合并，冲突项保留目标。
     */
    private static void mergeMove(File source, File target) {
        if (!source.exists()) {
            return;
        }
        try {
            if (!target.exists()) {
                FileUtil.mkdir(target.getParentFile());
                FileUtil.move(source, target, true);
                return;
            }
            if (!source.isDirectory() || !target.isDirectory()) {
                return;
            }
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                mergeMove(child, new File(target, child.getName()));
            }
            File[] rest = source.listFiles();
            if (rest == null || rest.length == 0) {
                FileUtil.del(source);
            }
        } catch (Exception ignored) {
            // 迁移失败时保留旧文件，程序随时可以再次尝试。
        }
    }
}
