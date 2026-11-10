package ani.rss.entity;

import ani.rss.enums.NotificationStatusEnum;
import ani.rss.enums.NotificationTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
@Schema(description = "通知配置")
public class NotificationConfig implements Serializable {
    /**
     * 启用
     */
    @Schema(description = "启用")
    private Boolean enable;

    /**
     * 重试次数
     */
    @Schema(description = "重试次数")
    private Integer retry;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String comment;

    /**
     * 通知模版
     */
    @Schema(description = "通知模版")
    private String notificationTemplate;

    /**
     * 通知类型
     */
    @Schema(description = "通知类型")
    private NotificationTypeEnum notificationType;

    /**
     * telegram bot token
     */
    @Schema(description = "telegram bot token")
    private String telegramBotToken;
    /**
     * telegram chat_id
     */
    @Schema(description = "telegram chat_id")
    private String telegramChatId;
    /**
     * telegram topic id
     */
    @Schema(description = "telegram topic id")
    private Integer telegramTopicId;
    /**
     * telegram Api Host
     */
    @Schema(description = "telegram Api Host")
    private String telegramApiHost;
    /**
     * telegram 发送图片
     */
    @Schema(description = "telegram 发送图片")
    private Boolean telegramImage;
    /**
     * telegram 格式
     */
    @Schema(description = "telegram 格式")
    private String telegramFormat;


    /**
     * webHookMethod
     */
    @Schema(description = "WebHook 方法")
    private String webHookMethod;
    /**
     * webHookUrl
     */
    @Schema(description = "WebHook 地址")
    private String webHookUrl;
    /**
     * webHookHeader
     */
    @Schema(description = "WebHook Header")
    private String webHookHeader;
    /**
     * webHookBody
     */
    @Schema(description = "WebHook Body")
    private String webHookBody;

    /** 自定义请求的 Content-Type；旧 webhook 配置默认按 application/json 处理。 */
    @Schema(description = "自定义请求 Content-Type")
    private String webHookContentType;


    /**
     * emby扫描媒体库
     */
    @Schema(description = "Emby 扫描媒体库")
    private Boolean embyRefresh;
    /**
     * emby地址
     */
    @Schema(description = "Emby 地址")
    private String embyHost;
    /**
     * emby api密钥
     */
    @Schema(description = "Emby API 密钥")
    private String embyApiKey;
    /**
     * emby扫描媒体库
     */
    @Schema(description = "Emby 扫描媒体库 ID 列表")
    private List<String> embyRefreshViewIds;
    /**
     * emby延迟扫描
     */
    @Schema(description = "Emby 延迟扫描")
    private Long embyDelayed;

    /**
     * 通知 状态
     */
    @Schema(description = "通知状态")
    private List<NotificationStatusEnum> statusList;

    /**
     * 顺序
     */
    @Schema(description = "顺序")
    private Long sort;

    public static NotificationConfig createNotificationConfig() {
        NotificationConfig notificationConfig = new NotificationConfig();

        notificationConfig
                .setEnable(true)
                .setRetry(3)
                .setSort(10L)
                .setNotificationType(NotificationTypeEnum.TELEGRAM)
                .setNotificationTemplate("${notification}")
                .setComment("")
                .setStatusList(List.of(
                        NotificationStatusEnum.DOWNLOAD_START,
                        NotificationStatusEnum.OMIT,
                        NotificationStatusEnum.ERROR
                ));

        // telegram
        notificationConfig
                .setTelegramChatId("")
                .setTelegramBotToken("")
                .setTelegramApiHost("https://api.telegram.org")
                .setTelegramImage(true)
                .setTelegramTopicId(-1)
                .setTelegramFormat("");

        // webhook
        notificationConfig
                .setWebHookBody("")
                .setWebHookUrl("")
                .setWebHookHeader("")
                .setWebHookMethod("POST")
                .setWebHookContentType("application/json");

        // emby
        notificationConfig
                .setEmbyRefresh(false)
                .setEmbyApiKey("")
                .setEmbyRefreshViewIds(new ArrayList<>())
                .setEmbyDelayed(0L);

        return notificationConfig;
    }


}
