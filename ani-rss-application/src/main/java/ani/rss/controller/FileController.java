package ani.rss.controller;

import ani.rss.annotation.Auth;
import ani.rss.commons.ExceptionUtils;
import ani.rss.commons.FileUtils;
import ani.rss.entity.Global;
import ani.rss.entity.web.Header;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@RestController
public class FileController extends BaseController {

    @Auth
    @Operation(summary = "获取文件")
    @GetMapping("/file")
    public void file(@RequestParam("filename") String filename) {
        filename = filename.replace(" ", "+");
        filename = Base64.decodeStr(filename);

        verifyFileFormat(filename);
        doFile(filename);
    }

    /** Serve only images uploaded through the dedicated branding asset endpoint. */
    @Operation(summary = "获取 WebUI 自定义图片")
    @GetMapping("/uiAsset")
    public void uiAsset(@RequestParam("filename") String filename) {
        HttpServletResponse response = Global.RESPONSE.get();
        try {
            Path relative = Path.of(filename).normalize();
            Assert.isTrue(!relative.isAbsolute() && !relative.startsWith(".."), "不允许访问");
            String extension = FileUtil.extName(relative.getFileName().toString()).toLowerCase();
            String contentType = switch (extension) {
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "webp" -> "image/webp";
                case "gif" -> "image/gif";
                default -> throw new IllegalArgumentException("不允许访问");
            };
            Path filesRoot = Path.of(ConfigUtil.getConfigDir().getAbsolutePath(), "ui-assets").normalize();
            Path image = filesRoot.resolve(relative).normalize();
            Assert.isTrue(image.startsWith(filesRoot) && Files.isRegularFile(image), "文件不存在");
            response.setContentType(contentType);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "public, max-age=86400");
            Files.copy(image, response.getOutputStream());
        } catch (Exception e) {
            response.setStatus(404);
        }
    }

    /**
     * 校验文件格式
     */
    private void verifyFileFormat(String filename) {
        Assert.notBlank(filename, "不允许访问");

        String extName = FileUtil.extName(filename);

        Assert.notBlank(extName, "不允许访问");

        Assert.isTrue(FileUtils.isImageFormat(filename), "不允许访问");
    }

    /**
     * 处理文件
     *
     * @param filename 文件名
     */
    private void doFile(String filename) {
        HttpServletResponse response = Global.RESPONSE.get();

        File file = new File(filename);
        if (!file.exists()) {
            File configDir = ConfigUtil.getConfigDir();
            file = Path.of(configDir.toString(), "files", filename).toFile();
            if (!file.exists()) {
                writeNotFound();
                return;
            }
        }

        long fileLength = file.length();
        String contentType = getContentType(file.getName());

        response.setHeader(Header.CONTENT_DISPOSITION, StrFormatter.format("inline; filename=\"{}\"", URLUtil.encode(file.getName())));
        long maxAge = 0;

        // 小于或者等于 3M 缓存
        if (fileLength <= 1024 * 1024 * 3) {
            // 30 天
            maxAge = 86400 * 30;
        }
        setCacheControl(response, maxAge);
        response.setContentType(contentType);

        try {
            response.setContentLengthLong(fileLength);
            @Cleanup
            InputStream inputStream = FileUtil.getInputStream(file);
            @Cleanup
            var outputStream = response.getOutputStream();
            IoUtil.copy(inputStream, outputStream);
        } catch (Exception e) {
            String message = ExceptionUtils.getMessage(e);
            log.debug(message, e);
        }
    }
}
