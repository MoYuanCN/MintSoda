package ani.rss.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum NotificationTypeEnum {
    EMBY_REFRESH,
    TELEGRAM,
    CUSTOM_REQUEST,
    /** 仅用于读取旧版配置，加载时会迁移为 CUSTOM_REQUEST。 */
    WEB_HOOK
}
