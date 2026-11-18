package ani.rss.util.other;

import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.entity.NotificationConfig;
import ani.rss.enums.NotificationStatusEnum;
import ani.rss.enums.NotificationTypeEnum;
import ani.rss.notification.*;
import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class NotificationUtil {
    private static final ExecutorService EXECUTOR_SERVICE = ExecutorBuilder.create()
            .setCorePoolSize(1)
            .setMaxPoolSize(1)
            .setWorkQueue(new LinkedBlockingQueue<>(256))
            .build();

    // WEB_HOOK 仅用于读取旧配置，发送时复用自定义请求。
    public final static Map<NotificationTypeEnum, Class<? extends BaseNotification>>
            NOTIFICATION_MAP =
            Map.of(
                    NotificationTypeEnum.EMBY_REFRESH, EmbyRefreshNotification.class,
                    NotificationTypeEnum.TELEGRAM, TelegramNotification.class,
                    NotificationTypeEnum.CUSTOM_REQUEST, CustomRequestNotification.class,
                    NotificationTypeEnum.WEB_HOOK, CustomRequestNotification.class,
                    NotificationTypeEnum.PLUGIN, PluginNotification.class
            );

    /**
     * 发送通知
     *
     * @param config                 设置
     * @param ani                    订阅
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     */
    public static void send(Config config, Ani ani, String text, NotificationStatusEnum notificationStatusEnum) {
        // 旧配置或导入的订阅可能缺少该字段，缺省按开启处理，避免下载流程在通知阶段中断。
        Boolean isMessage = ObjectUtil.defaultIfNull(ani.getMessage(), true);

        if (!isMessage) {
            // 未开启此订阅通知
            return;
        }

        List<NotificationConfig> notificationConfigList = config.getNotificationConfigList();
        notificationConfigList = notificationConfigList
                .stream()
                .sorted(Comparator.comparingLong(NotificationConfig::getSort))
                .toList();

        for (NotificationConfig notificationConfig : notificationConfigList) {
            boolean enable = notificationConfig.getEnable();
            int retry = notificationConfig.getRetry();
            NotificationTypeEnum notificationType = notificationConfig.getNotificationType();
            List<NotificationStatusEnum> statusList = notificationConfig.getStatusList();

            // 通知状态可能被删除
            statusList = statusList.stream().filter(Objects::nonNull).toList();

            if (!enable) {
                // 未开启
                continue;
            }

            if (!statusList.contains(notificationStatusEnum)) {
                // 未启用 通知状态
                continue;
            }

            if (Objects.isNull(notificationType)) {
                // 通知类型可能已经被删除
                continue;
            }

            if (!NOTIFICATION_MAP.containsKey(notificationType)) {
                continue;
            }
            if (notificationType == NotificationTypeEnum.PLUGIN
                    && StrUtil.isBlank(notificationConfig.getPluginChannelId())) {
                // 插件被卸载或渠道未选择时静默跳过，避免每条通知都报错。
                continue;
            }

            Class<? extends BaseNotification> aClass = NOTIFICATION_MAP.get(notificationType);

            BaseNotification baseNotification = ReflectUtil.newInstance(aClass);
            EXECUTOR_SERVICE.execute(() -> {
                int currentRetry = 0;
                do {
                    if (currentRetry > 0) {
                        log.warn("通知失败 正在重试 第{}次 {}", currentRetry, aClass.getName());
                    }
                    try {
                        baseNotification.send(notificationConfig, ani, text, notificationStatusEnum);
                        return;
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                    currentRetry += 1;
                    ThreadUtil.sleep(1000);
                } while (currentRetry < retry);
            });
        }
    }
}
