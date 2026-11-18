package ani.rss.controller;

import ani.rss.annotation.Auth;
import ani.rss.entity.web.Result;
import ani.rss.plugin.PluginManager;
import ani.rss.plugin.api.PluginEndpoint;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件自定义接口的分发入口。
 * <p>
 * 所有请求都要求登录，路径为 {@code /api/plugin/api/{pluginId}/**}；
 * 插件只能看到方法、路径、查询参数与请求体，不直接接触 Servlet 对象。
 */
@RestController
@RequiredArgsConstructor
public class PluginEndpointController extends BaseController {

    private static final int MAX_BODY_LENGTH = 256 * 1024;

    private final PluginManager pluginManager;

    @Auth
    @Operation(summary = "调用插件接口")
    @RequestMapping("/plugin/api/{pluginId}/**")
    public Result<Object> invoke(@PathVariable("pluginId") String pluginId,
                                 HttpServletRequest request) {
        String prefix = "/api/plugin/api/" + pluginId;
        String uri = StrUtil.nullToEmpty(request.getRequestURI());
        String path = StrUtil.removePrefix(uri, prefix);
        path = StrUtil.strip(path, "/");

        PluginEndpoint endpoint = pluginManager.endpoint(pluginId, path)
                .orElseThrow(() -> new IllegalArgumentException("插件接口不存在"));
        String method = StrUtil.nullToEmpty(request.getMethod()).toUpperCase();
        if (!endpoint.methods().isEmpty() && !endpoint.methods().contains(method)) {
            throw new IllegalArgumentException("插件接口不支持该请求方法");
        }

        Map<String, String> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) ->
                query.put(key, values == null || values.length == 0 ? "" : values[0]));

        String body = readBody(request);
        PluginEndpoint.EndpointRequest endpointRequest = new PluginEndpoint.EndpointRequest(
                method, path, query, body, StrUtil.nullToEmpty(request.getContentType()));
        try {
            String response = endpoint.handle(endpointRequest);
            return Result.success(StrUtil.nullToEmpty(response));
        } catch (Exception e) {
            throw new IllegalStateException("插件接口执行失败: " + e.getMessage());
        }
    }

    private static String readBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
                if (builder.length() > MAX_BODY_LENGTH) {
                    throw new IllegalArgumentException("插件接口请求体过大");
                }
            }
            return builder.toString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return "";
        }
    }
}
