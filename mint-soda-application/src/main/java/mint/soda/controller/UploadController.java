package mint.soda.controller;

import mint.soda.annotation.Auth;
import mint.soda.entity.web.Result;
import mint.soda.entity.web.ResultCode;
import mint.soda.util.other.ConfigUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.crypto.SecureUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Cleanup;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@RestController
public class UploadController extends BaseController {
    @Auth
    @Operation(summary = "上传 WebUI 外观图片")
    @PostMapping(value = "/uiAsset/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadUiAsset(@RequestParam("file") MultipartFile file) throws IOException {
        String extension = FileUtil.extName(file.getOriginalFilename()).toLowerCase();
        if (!java.util.Set.of("png", "jpg", "jpeg", "webp", "gif").contains(extension)) {
            throw new IllegalArgumentException("仅支持 PNG、JPG、WEBP 或 GIF 图片");
        }
        File tempFile = FileUtil.createTempFile();
        try (InputStream inputStream = file.getInputStream()) {
            FileUtil.writeFromStream(inputStream, tempFile);
        }
        if (!isSupportedImage(tempFile, extension)) {
            FileUtil.del(tempFile);
            throw new IllegalArgumentException("图片内容与文件格式不匹配");
        }
        String saveName = SecureUtil.md5(tempFile) + "." + extension;
        File assetDir = new File(ConfigUtil.getConfigDir(), "ui-assets");
        FileUtil.mkdir(assetDir);
        FileUtil.move(tempFile, new File(assetDir, saveName), true);
        return Result.<String>success("上传完成").setData(saveName);
    }

    private static boolean isSupportedImage(File file, String extension) throws IOException {
        byte[] header = new byte[12];
        try (InputStream inputStream = FileUtil.getInputStream(file)) {
            if (inputStream.read(header) < 8) return false;
        }
        return switch (extension) {
            case "png" -> (header[0] & 255) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
            case "jpg", "jpeg" -> (header[0] & 255) == 0xFF && (header[1] & 255) == 0xD8 && (header[2] & 255) == 0xFF;
            case "gif" -> header[0] == 'G' && header[1] == 'I' && header[2] == 'F';
            case "webp" -> header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }

    @Auth
    @Operation(summary = "上传文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        @Cleanup
        InputStream inputStream = file.getInputStream();

        File tempFile = FileUtil.createTempFile();
        FileUtil.writeFromStream(inputStream, tempFile);

        String s = SecureUtil.md5(tempFile);
        String fileName = file.getOriginalFilename();
        String saveName = s + "." + FileUtil.extName(fileName);

        File configDir = ConfigUtil.getConfigDir();
        File dir = new File(configDir, "/files/" + s.charAt(0));
        File saveFile = new File(dir, saveName);
        FileUtil.move(tempFile, saveFile, true);
        return new Result<>()
                .setCode(ResultCode.HTTP_OK)
                .setMessage("上传完成")
                .setData(s.charAt(0) + "/" + saveName);
    }

    @Auth
    @Operation(summary = "上传并读取为 base64")
    @PostMapping(value = "/uploadAndReadToBase64", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAndReadToBase64(@RequestParam("file") MultipartFile file) throws IOException {
        @Cleanup
        InputStream inputStream = file.getInputStream();

        String base64 = Base64.encode(inputStream);

        return new Result<String>().setData(base64);
    }

    @Auth
    @Operation(summary = "上传并读取")
    @PostMapping(value = "/uploadAndRead", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAndRead(@RequestParam("file") MultipartFile file) throws IOException {
        @Cleanup
        InputStream inputStream = file.getInputStream();

        String string = IoUtil.readUtf8(inputStream);

        return new Result<String>().setData(string);
    }
}
