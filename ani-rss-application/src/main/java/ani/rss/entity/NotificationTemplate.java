package ani.rss.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.Serializable;
import java.util.UUID;

/** A reusable plain-text or JSON notification template. */
@Data
@Accessors(chain = true)
@Schema(description = "通知模板")
public class NotificationTemplate implements Serializable {
    public static final String DEFAULT_TEXT_CONTENT = """
            ${emoji}${emoji}${emoji}
            事件类型: ${action}
            标题: ${title}
            评分: ${score}
            TMDB: ${tmdburl}
            TMDB标题: ${themoviedbName}
            BGM: ${bgmUrl}
            季: ${season}
            集: ${episode}
            字幕组: ${subgroup}
            进度: ${currentEpisodeNumber}/${totalEpisodeNumber}
            首播:  ${year}年${month}月${date}日
            事件: ${text}
            下载位置: ${downloadPath}
            TMDB集标题: ${episodeTitle}
            ${emoji}${emoji}${emoji}
            """.trim();

    public static final String DEFAULT_JSON_CONTENT = """
            {
              "event_type": "${action}",
              "title": "${title}",
              "score": "${score}",
              "tmdb_url": "${tmdburl}",
              "tmdb_title": "${themoviedbName}",
              "bgm_url": "${bgmUrl}",
              "season": "${season}",
              "episode": "${episode}",
              "subgroup": "${subgroup}",
              "progress": "${currentEpisodeNumber}/${totalEpisodeNumber}",
              "premiere_date": "${year}-${month}-${date}",
              "event": "${text}",
              "download_path": "${downloadPath}",
              "tmdb_episode_title": "${episodeTitle}"
            }
            """.trim();

    /** Previous built-in JSON template before templates gained per-field placeholders. */
    public static final String LEGACY_DEFAULT_JSON_CONTENT = "{\"text\":\"${notification}\"}";

    private String id = UUID.randomUUID().toString();
    private String name;

    /** Template content type: TEXT or JSON. */
    private String type;

    /** Shared content used by the selected template type. */
    private String content;

    /** Legacy text-template field retained for configuration compatibility. */
    private String telegramTemplate;

    /** Legacy JSON-template field retained for configuration compatibility. */
    private String customRequestBody;

    /** Expand the legacy nested notification placeholder while preserving valid JSON. */
    public static String replaceLegacyNotificationPlaceholder(String content, String notificationContent, boolean json) {
        if (content == null || !content.contains("${notification}")) return content;
        if (!json) return content.replace("${notification}", notificationContent);
        try {
            return replaceLegacyPlaceholder(JsonParser.parseString(content), notificationContent).toString();
        } catch (Exception ignored) {
            return content;
        }
    }

    private static JsonElement replaceLegacyPlaceholder(JsonElement element, String notificationContent) {
        if (element.isJsonObject()) {
            JsonObject replaced = new JsonObject();
            for (var entry : element.getAsJsonObject().entrySet()) {
                replaced.add(entry.getKey(), replaceLegacyPlaceholder(entry.getValue(), notificationContent));
            }
            return replaced;
        }
        if (element.isJsonArray()) {
            JsonArray replaced = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) {
                replaced.add(replaceLegacyPlaceholder(item, notificationContent));
            }
            return replaced;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(element.getAsString().replace("${notification}", notificationContent));
        }
        return element.deepCopy();
    }
}
