package ani.rss.notification;

import ani.rss.entity.Ani;
import ani.rss.entity.NotificationConfig;
import ani.rss.enums.NotificationStatusEnum;
import ani.rss.plugin.PluginManager;
import ani.rss.plugin.api.NotificationChannel;
import ani.rss.plugin.api.NotificationPayload;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;

import java.util.List;
import java.util.Map;

/**
 * 插件通知渠道的适配器。
 * <p>
 * 渠道字段由插件声明，值保存在通知配置的 {@code pluginOptions} 中。
 * 正文沿用统一模板渲染，插件只需关心如何投递。
 */
public class PluginNotification implements BaseNotification {

    @Override
    public void test(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum status) {
        NotificationChannel channel = channel(notificationConfig);
        String body = replaceNotificationTemplate(ani, notificationConfig, text, status);
        try {
            channel.test(payload(ani, body, status), options(notificationConfig));
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public Boolean send(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum status) {
        NotificationChannel channel = channel(notificationConfig);
        String body = replaceNotificationTemplate(ani, notificationConfig, text, status);
        try {
            channel.send(payload(ani, body, status), options(notificationConfig));
            return true;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static NotificationChannel channel(NotificationConfig config) {
        String channelId = StrUtil.trimToEmpty(config.getPluginChannelId());
        if (StrUtil.isBlank(channelId)) {
            throw new IllegalArgumentException("未选择插件通知渠道");
        }
        PluginManager manager = SpringUtil.getBean(PluginManager.class);
        NotificationChannel channel = manager.notificationChannels().get(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("插件通知渠道不可用: " + channelId);
        }
        return channel;
    }

    private static Map<String, String> options(NotificationConfig config) {
        return config.getPluginOptions() == null ? Map.of() : Map.copyOf(config.getPluginOptions());
    }

    private static NotificationPayload payload(Ani ani, String text, NotificationStatusEnum status) {
        String cover = StrUtil.blankToDefault(ani.getImage(), ani.getCover());
        return new NotificationPayload(
                status.name(),
                status.getAction(),
                StrUtil.nullToEmpty(ani.getTitle()),
                StrUtil.nullToEmpty(ani.getSubgroup()),
                StrUtil.nullToEmpty(text),
                String.valueOf(ani.getCurrentEpisodeNumber()),
                StrUtil.nullToEmpty(cover),
                ani.getCustomTags() == null ? List.of() : ani.getCustomTags()
        );
    }
}
