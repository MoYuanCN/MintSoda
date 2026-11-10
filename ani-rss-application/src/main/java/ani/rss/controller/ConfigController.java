package ani.rss.controller;

import ani.rss.annotation.Auth;
import ani.rss.entity.Config;
import ani.rss.entity.DeveloperSettings;
import ani.rss.entity.Login;
import ani.rss.entity.Global;
import ani.rss.entity.ProxyTest;
import ani.rss.entity.web.ContentType;
import ani.rss.entity.web.Result;
import ani.rss.service.ConfigService;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ObjectUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class ConfigController extends BaseController {

    @Resource
    private ConfigService configService;

    @Auth
    @Operation(summary = "获取设置")
    @PostMapping("/config")
    public Result<Config> config() {
        Config config = configService.config();
        return Result.success(config);
    }

    /** Public, non-sensitive appearance settings used to render the login screen. */
    @Operation(summary = "获取公开的外观设置")
    @PostMapping("/publicSettings")
    public Result<java.util.Map<String, Object>> publicSettings() {
        Config config = ConfigUtil.CONFIG;
        java.util.Map<String, Object> settings = new java.util.LinkedHashMap<>();
        settings.put("appBackgroundImage", StrUtil.nullToEmpty(config.getAppBackgroundImage()));
        settings.put("loginBackgroundImage", StrUtil.nullToEmpty(config.getLoginBackgroundImage()));
        settings.put("loginLogoImage", StrUtil.nullToEmpty(config.getLoginLogoImage()));
        settings.put("appLogoImage", StrUtil.nullToEmpty(config.getAppLogoImage()));
        settings.put("cardOpacity", ObjectUtil.defaultIfNull(config.getCardOpacity(), 92));
        settings.put("cardBlur", ObjectUtil.defaultIfNull(config.getCardBlur(), 8));
        settings.put("appCornerRadius", ObjectUtil.defaultIfNull(config.getAppCornerRadius(), 12));
        return Result.success(settings);
    }

    @Auth
    @Operation(summary = "验证开发者选项凭据")
    @PostMapping("/verifyDeveloperCredentials")
    public Result<Boolean> verifyDeveloperCredentials(@RequestBody Login credentials) {
        boolean valid = ConfigUtil.CONFIG.getLogin().getUsername().equals(credentials.getUsername())
                && ConfigUtil.CONFIG.getLogin().getPassword().equals(credentials.getPassword());
        if (!valid) return Result.error("用户名或密码错误");
        return Result.success(true);
    }

    @Auth
    @Operation(summary = "保存开发者选项")
    @PostMapping("/setDeveloperSettings")
    public Result<Void> setDeveloperSettings(@RequestBody DeveloperSettings settings) {
        boolean valid = ConfigUtil.CONFIG.getLogin().getUsername().equals(settings.getUsername())
                && ConfigUtil.CONFIG.getLogin().getPassword().equals(settings.getPassword());
        if (!valid) return Result.error("用户名或密码错误");
        String customUrl = StrUtil.trimToEmpty(settings.getCustomTorrentUrl());
        if (StrUtil.isNotBlank(customUrl)
                && !(customUrl.startsWith("http://") || customUrl.startsWith("https://"))) {
            return Result.error("自定义种子地址必须以 http:// 或 https:// 开头");
        }
        ConfigUtil.CONFIG.setCustomTorrentUrl(customUrl);
        ConfigUtil.sync();
        return Result.success("开发者选项已保存");
    }

    @Auth
    @Operation(summary = "修改设置")
    @PostMapping("/setConfig")
    public Result<Void> setConfig(@RequestBody Config newConfig) {
        configService.setConfig(newConfig);
        return Result.success("修改成功");
    }

    @Auth
    @Operation(summary = "清理缓存")
    @PostMapping("/clearCache")
    public Result<Void> clearCache() {
        String formatSize = configService.clearCache();
        return Result.success("清理完成, 共清理 {}", formatSize);
    }

    @Auth
    @Operation(summary = "代理测试")
    @PostMapping("/testProxy")
    public Result<ProxyTest> testProxy(@RequestParam("url") String url, @RequestBody Config config) {
        return Result.success(configService.testProxy(url, config));
    }

    @Auth
    @Operation(summary = "下载器测试")
    @PostMapping("/downloadLoginTest")
    public Result<Void> downloadLoginTest(@RequestBody Config config) {
        Boolean login = configService.downloadLoginTest(config);
        if (login) {
            return Result.success("登录成功");
        }
        return Result.error("登录失败");
    }

    @Operation(summary = "存活测试")
    @RequestMapping("/ping")
    public Result<Void> ping() {
        return Result.success();
    }

    @Operation(summary = "自定义JS")
    @GetMapping("/custom.js")
    public void customJs() {
        HttpServletResponse response = Global.RESPONSE.get();
        setCacheControl(response, 0);

        String customJs = ConfigUtil.CONFIG.getCustomJs();
        customJs = StrUtil.blankToDefault(customJs, "// empty js");

        write(200, ContentType.JAVASCRIPT, customJs);
    }

    @Operation(summary = "自定义CSS")
    @GetMapping("/custom.css")
    public void customCss() {
        HttpServletResponse response = Global.RESPONSE.get();
        setCacheControl(response, 0);

        String customCss = ConfigUtil.CONFIG.getCustomCss();
        customCss = StrUtil.blankToDefault(customCss, "/* empty css */");

        write(200, ContentType.TEXT_CSS, customCss);
    }
}
