package mint.soda.util.other;

import mint.soda.entity.Ani;
import mint.soda.entity.NotificationStatusStyle;
import mint.soda.enums.NotificationStatusEnum;
import cn.hutool.core.util.StrUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知状态的展示取数。
 * <p>
 * 状态的表情、名称与事件模板都可以在设置中覆盖，缺省时回落到枚举默认值。
 * 事件模板在发送前统一在这里渲染，发送端只需要提供资源名与来源标记，
 * 不再直接拼接中文文案。
 */
public final class NotificationStatusUtil {

    private NotificationStatusUtil() {
    }

    /**
     * 生成包含全部状态的默认样式表。
     *
     * @return 默认样式
     */
    public static Map<String, NotificationStatusStyle> defaultStyles() {
        Map<String, NotificationStatusStyle> styles = new LinkedHashMap<>();
        for (NotificationStatusEnum status : NotificationStatusEnum.values()) {
            styles.put(status.name(), new NotificationStatusStyle()
                    .setEmoji(status.getDefaultEmoji())
                    .setLabel(status.getDefaultLabel())
                    .setTemplate(status.getDefaultTemplate()));
        }
        return styles;
    }

    /**
     * 读取状态样式；配置缺失字段时回落到默认值。
     *
     * @param status 通知状态
     * @return 状态样式
     */
    public static NotificationStatusStyle style(NotificationStatusEnum status) {
        NotificationStatusStyle fallback = new NotificationStatusStyle()
                .setEmoji(status.getDefaultEmoji())
                .setLabel(status.getDefaultLabel())
                .setTemplate(status.getDefaultTemplate());

        Map<String, NotificationStatusStyle> styles = ConfigUtil.CONFIG.getNotificationStatusStyles();
        if (styles == null) {
            return fallback;
        }
        NotificationStatusStyle style = styles.get(status.name());
        if (style == null) {
            return fallback;
        }
        return new NotificationStatusStyle()
                .setEmoji(StrUtil.blankToDefault(style.getEmoji(), fallback.getEmoji()))
                .setLabel(StrUtil.blankToDefault(style.getLabel(), fallback.getLabel()))
                .setTemplate(StrUtil.blankToDefault(style.getTemplate(), fallback.getTemplate()));
    }

    /**
     * 表情
     *
     * @param status 通知状态
     * @return 表情
     */
    public static String emoji(NotificationStatusEnum status) {
        return style(status).getEmoji();
    }

    /**
     * 状态名称
     *
     * @param status 通知状态
     * @return 状态名称
     */
    public static String label(NotificationStatusEnum status) {
        return style(status).getLabel();
    }

    /**
     * 渲染事件文案。
     *
     * @param status 通知状态
     * @param ani    订阅，可为空
     * @param name   资源名或集数描述
     * @param source 来源标记，例如“备用RSS”
     * @return 事件文案
     */
    public static String text(NotificationStatusEnum status, Ani ani, String name, String source) {
        String template = style(status).getTemplate();
        String prefix = StrUtil.isBlank(source) ? "" : "(" + source + ") ";
        String result = template
                .replace("${name}", StrUtil.nullToEmpty(name))
                .replace("${source}", prefix);
        if (ani != null) {
            result = TemplateVariables.from(ani).apply(result);
        }
        return result.trim();
    }
}
