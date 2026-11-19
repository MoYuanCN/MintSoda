package ani.rss.controller;

import ani.rss.annotation.Auth;
import ani.rss.commons.MavenUtils;
import ani.rss.entity.Global;
import ani.rss.entity.web.Header;
import ani.rss.entity.web.Result;
import ani.rss.service.BackupService;
import ani.rss.plugin.PluginManager;
import ani.rss.service.PluginArchive;
import ani.rss.service.TaskService;
import ani.rss.util.other.AniUtil;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
@RestController
public class BackupController extends BaseController {
    @Resource
    private BackupService backupService;
    @Resource
    private TaskService taskService;
    @Resource
    private PluginManager pluginManager;

    @Auth
    @Operation(summary = "导出备份")
    @GetMapping("/exportBackup")
    public void backupBackup() throws IOException {
        String version = MavenUtils.getVersion();
        String filename = StrUtil.format("ani-rss.backup.{}.zip", version);

        String contentType = getContentType(filename);

        HttpServletResponse response = Global.RESPONSE.get();

        response.setContentType(contentType);
        response.setHeader(Header.CONTENT_DISPOSITION, StrFormatter.format("inline; filename=\"{}\"", filename));

        @Cleanup
        OutputStream outputStream = response.getOutputStream();

        backupService.backup(outputStream);
    }

    @Auth
    @Operation(summary = "导入备份")
    @PostMapping(value = "/importBackup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> importBackup(@RequestParam("file") MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extName = FileUtil.extName(originalFilename);
        Assert.isTrue("zip".equals(extName), "导入格式异常");

        File configDir = ConfigUtil.getConfigDir();

        @Cleanup
        InputStream inputStream = file.getInputStream();

        // 单遍解压：普通配置直接落盘，插件条目写入临时目录。
        // 插件包含脚本与私有数据，路径必须逐条校验并整目录替换，不能交给通用解压。
        java.nio.file.Path pluginStaging = PluginArchive.newStaging(configDir);
        try {
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(inputStream)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                    zip.transferTo(buffer);
                    if (PluginArchive.isPluginEntry(entry.getName())) {
                        PluginArchive.writeEntry(pluginStaging, entry.getName(), buffer.toByteArray());
                        continue;
                    }
                    java.nio.file.Path target = configDir.toPath()
                            .resolve(entry.getName().replace('\\', '/')).normalize();
                    Assert.isTrue(target.startsWith(configDir.toPath().toAbsolutePath().normalize()),
                            "备份包含非法路径: " + entry.getName());
                    java.nio.file.Files.createDirectories(target.getParent());
                    java.nio.file.Files.write(target, buffer.toByteArray());
                }
            }

            // 删除旧的种子记录
            FileUtil.del(new File(configDir, "torrents"));
            int restored = PluginArchive.commit(pluginStaging, configDir);
            if (restored > 0) {
                log.info("已从备份恢复 {} 个插件", restored);
            }
        } catch (Exception e) {
            FileUtil.del(pluginStaging.toFile());
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IllegalArgumentException("备份导入失败: " + e.getMessage(), e);
        }

        // 重新加载设置与插件；插件目录可能已被整体替换。
        ConfigUtil.load();
        AniUtil.load();
        pluginManager.rescan();
        taskService.restart();

        return Result.success("导入成功");
    }
}
