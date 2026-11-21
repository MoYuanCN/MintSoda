package mint.soda.enums;

import lombok.Getter;

/**
 * 通知状态。
 * <p>
 * 表情、展示文案与事件模板都可以在设置中覆盖，这里只提供默认值；
 * 取值统一走 {@code NotificationStatusUtil}，不要在发送逻辑里直接拼接中文。
 */
@Getter
public enum NotificationStatusEnum {
    /**
     * 开始下载
     */
    DOWNLOAD_START("🎈", "开始下载", "${source}${name} 已加入下载"),
    /**
     * 下载完成
     */
    DOWNLOAD_END("🎉", "下载完成", "${source}${name} 下载完成"),
    /**
     * 缺集
     */
    OMIT("⛔", "缺少集数", "${title} 缺少集数 ${name}"),
    /**
     * 错误
     */
    ERROR("❌", "发生错误", "${source}${name} 添加失败，疑似为坏种"),
    /**
     * 订阅完结
     */
    COMPLETED("🎊", "订阅完结", "${title} 订阅已完结");

    private final String defaultEmoji;
    private final String defaultLabel;
    private final String defaultTemplate;

    NotificationStatusEnum(String defaultEmoji, String defaultLabel, String defaultTemplate) {
        this.defaultEmoji = defaultEmoji;
        this.defaultLabel = defaultLabel;
        this.defaultTemplate = defaultTemplate;
    }
}
