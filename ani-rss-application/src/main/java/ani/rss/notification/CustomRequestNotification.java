package ani.rss.notification;

import ani.rss.commons.GsonStatic;
import ani.rss.entity.Ani;
import ani.rss.entity.NotificationConfig;
import ani.rss.enums.NotificationStatusEnum;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.Method;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Sends a configurable HTTP request with rendered notification templates. */
@Slf4j
public class CustomRequestNotification implements BaseNotification {
    @Override
    public void test(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum status) {
        send(notificationConfig, ani, text, status);
    }

    @Override
    public Boolean send(NotificationConfig config, Ani ani, String text, NotificationStatusEnum status) {
        String url = render(config.getWebHookUrl(), ani, text, status);
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("请求地址不能为空");
        }

        String methodName = StrUtil.blankToDefault(config.getWebHookMethod(), "POST").trim().toUpperCase(Locale.ROOT);
        Method method;
        try {
            method = Method.valueOf(methodName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的请求方法: " + methodName, e);
        }

        String contentType = StrUtil.blankToDefault(config.getWebHookContentType(), "application/json").trim();
        Map<String, String> headers = renderHeaders(config.getWebHookHeader(), ani, text, status);
        if (headers.keySet().stream().noneMatch(key -> key.equalsIgnoreCase("Content-Type"))) {
            headers.put("Content-Type", contentType);
        }

        String bodyTemplate = config.getWebHookBody();
        String body = StrUtil.isBlank(bodyTemplate) ? "" : isJson(contentType)
                ? renderJsonBody(bodyTemplate, ani, text, status)
                : render(bodyTemplate, ani, text, status);

        HttpRequest request = HttpReq.get(url)
                .addHeaders(headers)
                .method(method);
        if (StrUtil.isNotEmpty(body)) {
            request.body(body, contentType);
        }

        log.debug("自定义请求 {} {}", method, url);
        return request.thenFunction(response -> {
            if (response.isOk()) {
                return true;
            }
            throw new IllegalStateException("自定义请求失败，状态码: " + response.getStatus());
        });
    }

    private static String render(String template, Ani ani, String text, NotificationStatusEnum status) {
        if (StrUtil.isBlank(template)) {
            return StrUtil.nullToEmpty(template);
        }
        String rendered = new TemplateNotification().render(ani, template, text, status);
        rendered = rendered.replace("${image}", imageUrl(ani));
        if (rendered.contains("${imageBase64}")) {
            rendered = rendered.replace("${imageBase64}", imageBase64(ani));
        }
        return rendered;
    }

    private static String renderJsonBody(String template, Ani ani, String text, NotificationStatusEnum status) {
        JsonElement json;
        try {
            json = JsonParser.parseString(template);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 请求体模板格式无效。请将占位符放在 JSON 字符串值中，例如 {\"text\":\"${notification}\"}", e);
        }
        return GsonStatic.toJson(renderJson(json, ani, text, status));
    }

    private static JsonElement renderJson(JsonElement json, Ani ani, String text, NotificationStatusEnum status) {
        if (json.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                result.add(entry.getKey(), renderJson(entry.getValue(), ani, text, status));
            }
            return result;
        }
        if (json.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement item : json.getAsJsonArray()) {
                result.add(renderJson(item, ani, text, status));
            }
            return result;
        }
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            return GsonStatic.GSON.toJsonTree(render(json.getAsString(), ani, text, status));
        }
        return json.deepCopy();
    }

    private static boolean isJson(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("application/json") || normalized.contains("+json");
    }

    private static Map<String, String> renderHeaders(String headerTemplate, Ani ani, String text,
                                                       NotificationStatusEnum status) {
        Map<String, String> headers = new HashMap<>();
        if (StrUtil.isBlank(headerTemplate)) {
            return headers;
        }
        for (String line : StrUtil.split(headerTemplate, "\n", true, true)) {
            int separator = line.indexOf(':');
            if (separator < 1) {
                continue;
            }
            String name = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (StrUtil.isNotBlank(name)) {
                headers.put(name, render(value, ani, text, status));
            }
        }
        return headers;
    }

    private static String imageUrl(Ani ani) {
        return Opt.ofNullable(ani).map(Ani::getImage).filter(StrUtil::isNotBlank).orElse("");
    }

    private static String imageBase64(Ani ani) {
        String emptyImage = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
        String cover = Opt.ofNullable(ani).map(Ani::getCover).orElse("");
        if (StrUtil.isBlank(cover)) {
            return emptyImage;
        }
        File file = new File(cover);
        if (!file.exists()) {
            file = Path.of(ConfigUtil.getConfigDir().toString(), "files", cover).toFile();
        }
        return file.exists() ? Base64.getEncoder().encodeToString(FileUtil.readBytes(file)) : emptyImage;
    }

    /** Adapter to use the existing, shared notification placeholder implementation. */
    private static final class TemplateNotification implements BaseNotification {
        @Override
        public void test(NotificationConfig config, Ani ani, String text, NotificationStatusEnum status) {
        }

        @Override
        public Boolean send(NotificationConfig config, Ani ani, String text, NotificationStatusEnum status) {
            return false;
        }

        private String render(Ani ani, String template, String text, NotificationStatusEnum status) {
            return replaceNotificationTemplate(ani, template, text, status);
        }
    }
}
