package mint.soda.service;

import mint.soda.commons.StoragePaths;
import mint.soda.util.other.AniUtil;
import mint.soda.util.other.ConfigUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 插件相关文件的导入导出。
 * <p>
 * 从备份服务中拆出：备份关心“哪些目录要打包”，这里关心“插件部分怎么安全地读写”。
 * <p>
 * 导出时保留插件目录结构（描述文件、脚本与 {@code data/}），导入时逐条校验路径，
 * 拒绝绝对路径与 {@code ..}，并在替换前先写入临时目录，避免半个插件覆盖现有安装。
 */
public final class PluginArchive {

    /**
     * 压缩包内插件目录的前缀
     */
    public static final String PREFIX = "data/plugins/";

    /**
     * 旧备份中的插件目录前缀，导入时兼容
     */
    private static final String LEGACY_PREFIX = "plugins/";

    /**
     * 需要排除的运行时状态文件；安装记录由宿主重建，不随备份恢复
     */
    private static final List<String> EXCLUDED_FILES = List.of("installed.json");

    private PluginArchive() {
    }

    /**
     * 收集需要导出的插件目录。
     *
     * @return 插件根目录，不存在时返回空列表
     */
    public static List<File> exportRoots() {
        File root = mint.soda.plugin.PluginPaths.root();
        if (!root.isDirectory()) {
            return List.of();
        }
        return List.of(root);
    }

    /**
     * 判断压缩包条目是否应当写入。
     *
     * @param name 条目相对路径
     * @return 是否保留
     */
    public static boolean accept(String name) {
        String normalized = StrUtil.nullToEmpty(name).replace('\\', '/');
        if (normalized.isEmpty() || normalized.startsWith(".")) {
            return false;
        }
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return !EXCLUDED_FILES.contains(fileName);
    }

    /**
     * 导出插件目录。插件不存在时不写入任何条目。
     *
     * @param zip 已打开的压缩流
     */
    public static void exportTo(ZipOutputStream zip) throws IOException {
        File root = mint.soda.plugin.PluginPaths.root();
        if (!root.isDirectory()) {
            return;
        }
        Path base = root.toPath().toAbsolutePath().normalize();
        List<Path> paths = new ArrayList<>();
        try (var stream = Files.walk(base)) {
            stream.filter(Files::isRegularFile)
                    // 符号链接可能指向插件目录之外，导出前直接跳过。
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(paths::add);
        }
        for (Path path : paths) {
            String relative = base.relativize(path).toString().replace(File.separatorChar, '/');
            if (!accept(relative)) {
                continue;
            }
            ZipEntry entry = new ZipEntry(PREFIX + relative);
            zip.putNextEntry(entry);
            Files.copy(path, zip);
            zip.closeEntry();
        }
    }

    /**
     * 条目是否属于插件目录。
     */
    public static boolean isPluginEntry(String name) {
        String normalized = StrUtil.nullToEmpty(name).replace('\\', '/');
        return normalized.startsWith(PREFIX) || normalized.startsWith(LEGACY_PREFIX);
    }

    /**
     * 创建一个导出的临时目录。
     *
     * @param configDir 配置目录
     * @return 临时目录
     */
    public static Path newStaging(File configDir) throws IOException {
        Path staging = new File(StoragePaths.dataDir(), "plugins.import-" + System.currentTimeMillis())
                .toPath().toAbsolutePath().normalize();
        Files.createDirectories(staging);
        return staging;
    }

    /**
     * 把一个插件条目写入临时目录。
     * <p>
     * 路径先规范化再校验必须位于临时目录内，拒绝 {@code ..} 与绝对路径。
     *
     * @param staging 临时目录
     * @param name    条目名
     * @param content 条目内容
     * @return 是否写入
     */
    public static boolean writeEntry(Path staging, String name, byte[] content) throws IOException {
        String normalized = StrUtil.nullToEmpty(name).replace('\\', '/');
        String relative;
        if (normalized.startsWith(PREFIX)) {
            relative = normalized.substring(PREFIX.length());
        } else if (normalized.startsWith(LEGACY_PREFIX)) {
            relative = normalized.substring(LEGACY_PREFIX.length());
        } else {
            return false;
        }
        if (!accept(relative)) {
            return false;
        }
        Path target = staging.resolve(relative).normalize();
        Assert.isTrue(target.startsWith(staging), "备份包含非法路径: " + name);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return true;
    }

    /**
     * 用临时目录替换正式插件目录。临时目录为空时保持现有安装不变。
     *
     * @param staging   临时目录
     * @param configDir 配置目录
     * @return 恢复的插件数量
     */
    public static int commit(Path staging, File configDir) throws IOException {
        File pluginsRoot = StoragePaths.pluginsDir();
        try (var stream = Files.walk(staging)) {
            boolean empty = stream.filter(Files::isRegularFile).findAny().isEmpty();
            if (empty) {
                FileUtil.del(staging);
                return 0;
            }
        }
        int count;
        try (var stream = Files.walk(staging, 2)) {
            count = (int) stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("plugin.json"))
                    .filter(path -> staging.relativize(path).getNameCount() == 2)
                    .count();
        }
        // 校验通过后再替换正式目录，避免解包失败导致插件丢失。
        FileUtil.del(pluginsRoot);
        Files.move(staging, pluginsRoot.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return count;
    }

    /**
     * 序列化到输出流，供手工导出的入口复用。
     *
     * @param outputStream 输出流
     */
    public static void export(OutputStream outputStream) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            exportTo(zip);
        }
    }

    /**
     * 插件数据目录大小，供界面提示备份体积。
     *
     * @return 字节数
     */
    public static long size() {
        File root = mint.soda.plugin.PluginPaths.root();
        if (!root.isDirectory()) {
            return 0L;
        }
        try (var stream = Files.walk(root.toPath())) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .mapToLong(path -> path.toFile().length())
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }
}
