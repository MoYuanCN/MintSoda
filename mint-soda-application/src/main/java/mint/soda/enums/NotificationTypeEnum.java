package mint.soda.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum NotificationTypeEnum {
    EMBY_REFRESH,
    TELEGRAM,
    CUSTOM_REQUEST,
    /** 由插件提供的通知渠道 */
    PLUGIN,
    /** 仅用于读取旧版配置，加载时会迁移为 CUSTOM_REQUEST。 */
    WEB_HOOK
}
