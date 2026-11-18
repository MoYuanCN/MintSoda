package ani.rss.controller;

import ani.rss.annotation.Auth;
import ani.rss.entity.web.Result;
import ani.rss.plugin.PluginInfo;
import ani.rss.plugin.PluginMarketIndex;
import ani.rss.service.PluginService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 插件管理接口。所有接口都要求登录，安装与更新会写入可执行代码，属高权限操作。
 */
@RestController
@RequiredArgsConstructor
public class PluginController extends BaseController {

    private final PluginService pluginService;

    @Auth
    @Operation(summary = "插件列表")
    @PostMapping("/plugin/list")
    public Result<List<PluginInfo>> list() {
        return Result.success(pluginService.list());
    }

    @Auth
    @Operation(summary = "插件详情")
    @PostMapping("/plugin/info")
    public Result<PluginInfo> info(@RequestBody Map<String, String> body) {
        return Result.success(pluginService.info(body.get("id")));
    }

    @Auth
    @Operation(summary = "启用插件")
    @PostMapping("/plugin/enable")
    public Result<Void> enable(@RequestBody Map<String, String> body) {
        pluginService.enable(body.get("id"));
        return Result.success("插件已启用");
    }

    @Auth
    @Operation(summary = "停用插件")
    @PostMapping("/plugin/disable")
    public Result<Void> disable(@RequestBody Map<String, String> body) {
        pluginService.disable(body.get("id"));
        return Result.success("插件已停用");
    }

    @Auth
    @Operation(summary = "重新加载插件")
    @PostMapping("/plugin/reload")
    public Result<Void> reload(@RequestBody Map<String, String> body) {
        pluginService.reload(body.get("id"));
        return Result.success("插件已重新加载");
    }

    @Auth
    @Operation(summary = "卸载插件")
    @PostMapping("/plugin/uninstall")
    public Result<Void> uninstall(@RequestBody Map<String, String> body) {
        pluginService.uninstall(body.get("id"));
        return Result.success("插件已卸载");
    }

    @Auth
    @Operation(summary = "保存插件配置")
    @PostMapping("/plugin/saveConfig")
    public Result<Void> saveConfig(@RequestBody Map<String, String> body) {
        pluginService.saveConfig(body.get("id"), body.get("config"));
        return Result.success("插件配置已保存");
    }

    @Auth
    @Operation(summary = "上传安装插件")
    @PostMapping(value = "/plugin/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PluginInfo> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "sha256", required = false) String sha256) throws Exception {
        return Result.success(pluginService.installFromUpload(file, sha256));
    }

    @Auth
    @Operation(summary = "从链接安装插件")
    @PostMapping("/plugin/install")
    public Result<PluginInfo> install(@RequestBody Map<String, String> body) {
        return Result.success(pluginService.installFromUrl(
                body.get("url"), body.get("sha256"), parseSize(body.get("size"))));
    }

    @Auth
    @Operation(summary = "获取插件市场")
    @PostMapping("/plugin/market")
    public Result<PluginMarketIndex> market(@RequestBody(required = false) Map<String, String> body) {
        String url = body == null ? null : body.get("url");
        return Result.success(pluginService.market(url));
    }

    @Auth
    @Operation(summary = "从市场安装插件")
    @PostMapping("/plugin/installFromMarket")
    public Result<PluginInfo> installFromMarket(@RequestBody PluginMarketIndex.MarketEntry entry) {
        return Result.success(pluginService.installFromMarket(entry));
    }

    @Auth
    @Operation(summary = "检查插件更新")
    @PostMapping("/plugin/checkUpdate")
    public Result<PluginInfo> checkUpdate(@RequestBody Map<String, String> body) {
        return Result.success(pluginService.checkUpdate(body.get("id"), body.get("marketUrl")));
    }

    @Auth
    @Operation(summary = "检查全部插件更新")
    @PostMapping("/plugin/checkAll")
    public Result<List<PluginInfo>> checkAll(@RequestBody(required = false) Map<String, String> body) {
        String url = body == null ? null : body.get("marketUrl");
        return Result.success(pluginService.checkAll(url));
    }

    @Auth
    @Operation(summary = "更新插件")
    @PostMapping("/plugin/update")
    public Result<PluginInfo> update(@RequestBody Map<String, String> body) {
        return Result.success(pluginService.update(body.get("id"), body.get("marketUrl")));
    }

    @Auth
    @Operation(summary = "获取插件市场地址")
    @PostMapping("/plugin/marketUrl")
    public Result<String> marketUrl() {
        return Result.success(pluginService.marketUrl());
    }

    @Auth
    @Operation(summary = "保存插件市场地址")
    @PostMapping("/plugin/setMarketUrl")
    public Result<Void> setMarketUrl(@RequestBody Map<String, String> body) {
        pluginService.setMarketUrl(body.get("url"));
        return Result.success("插件市场地址已保存");
    }

    private static Long parseSize(String size) {
        if (size == null || size.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(size.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("插件包大小格式无效");
        }
    }
}
