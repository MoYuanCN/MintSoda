package ani.rss.service;

import ani.rss.commons.FileUtils;
import ani.rss.entity.Config;
import ani.rss.util.other.AniUtil;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jakarta.annotation.Resource;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
public class BackupService {

    @Resource
    private ClearService clearService;

    private static final Config CONFIG = ConfigUtil.CONFIG;

    /**
     * 备份
     */
    public synchronized void backup() {
        Boolean configBackup = CONFIG.getConfigBackup();
        if (!configBackup) {
            return;
        }

        clearBackup();

        File configDir = ConfigUtil.getConfigDir();
        File backupDir = new File(configDir, "backup");

        String date = DateUtil.format(new Date(), DatePattern.NORM_DATE_PATTERN);
        File backupFile = new File(backupDir, date + ".zip");

        if (backupFile.exists()) {
            return;
        }

        log.info("正在备份设置 {}", backupFile.getName());

        try {
            @Cleanup
            OutputStream outputStream = FileUtil.getOutputStream(backupFile);
            backup(outputStream);
            log.info("备份设置成功 {}", backupFile.getName());
        } catch (Exception e) {
            log.error("备份失败 {}", backupFile.getName());
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 备份设置
     *
     * @param outputStream 文件流
     */
    public synchronized void backup(OutputStream outputStream) throws java.io.IOException {
        // 清理残余封面
        clearService.clearCover();

        File configDir = ConfigUtil.getConfigDir();
        List<File> backupFiles = Stream.of(
                        "files", "torrents",
                        AniUtil.FILE_NAME, ConfigUtil.FILE_NAME
                )
                .map(s -> new File(configDir, s))
                .filter(File::exists)
                .toList();

        try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            // Hutool 的 zip(OutputStream, ...) 会关闭流，这里自行写条目，才能在同一压缩包里追加插件目录。
            for (File root : backupFiles) {
                if (root.isDirectory()) {
                    // 目录以名称做前缀，自身不写条目，避免空目录产生重复或陌生条目。
                    for (File child : FileUtils.listFiles(root)) {
                        writeEntry(zip, child, root.getName() + "/" + child.getName()
                                + (child.isDirectory() ? "/" : ""));
                    }
                } else {
                    writeEntry(zip, root, root.getName());
                }
            }
            // 插件目录单独遍历，确保脚本、描述文件与私有 data/ 一起备份，但不含安装状态。
            PluginArchive.exportTo(zip);
            zip.finish();
        }
    }

    /**
     * 递归写入一个文件或目录。
     *
     * @param zip    压缩流
     * @param file   文件
     * @param prefix 条目前缀
     */
    private static void writeEntry(ZipOutputStream zip, File file, String prefix) throws IOException {
        if (file.isDirectory()) {
            File[] children = FileUtils.listFiles(file);
            if (ArrayUtil.isEmpty(children)) {
                return;
            }
            for (File child : children) {
                writeEntry(zip, child, prefix + child.getName() + (child.isDirectory() ? "/" : ""));
            }
            return;
        }
        if (file.getName().startsWith(".")) {
            return;
        }
        zip.putNextEntry(new ZipEntry(prefix));
        Files.copy(file.toPath(), zip);
        zip.closeEntry();
    }

    /**
     * 清理备份
     */
    public synchronized void clearBackup() {
        Integer configBackupDay = CONFIG.getConfigBackupDay();

        // 过期时间
        long expirationTime = DateUtil.offsetDay(new Date(), -configBackupDay).getTime();

        File configDir = ConfigUtil.getConfigDir();
        File backupDir = new File(configDir, "backup");
        if (!backupDir.exists()) {
            return;
        }

        File[] files = FileUtils.listFiles(backupDir);
        if (ArrayUtil.isEmpty(files)) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            String extName = FileUtil.extName(file);
            if (!"zip".equals(extName)) {
                continue;
            }
            String mainName = FileUtil.mainName(file);
            try {
                long time = DateUtil.parse(mainName, DatePattern.NORM_DATE_PATTERN).getTime();
                if (time > expirationTime) {
                    continue;
                }
                log.info("{} 备份已过期, 自动删除", file.getName());
                FileUtil.del(file);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
