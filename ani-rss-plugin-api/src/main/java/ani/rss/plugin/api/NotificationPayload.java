package ani.rss.plugin.api;

import java.util.List;

/**
 * 通知内容快照。字段为可空值，插件应自行兜底。
 *
 * @param status      状态标识，例如 DOWNLOAD_START
 * @param statusLabel 状态中文描述
 * @param title       订阅标题
 * @param subgroup    字幕组
 * @param text        已渲染的通知正文
 * @param episode     当前集数
 * @param coverUrl    封面绝对地址，可为空
 * @param tags        自定义标签
 */
public record NotificationPayload(
        String status,
        String statusLabel,
        String title,
        String subgroup,
        String text,
        String episode,
        String coverUrl,
        List<String> tags
) {
    public NotificationPayload {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
