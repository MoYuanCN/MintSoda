package mint.soda.notification;

import mint.soda.commons.NumberFormatUtils;
import mint.soda.entity.Ani;
import mint.soda.entity.NotificationConfig;
import mint.soda.entity.NotificationTemplate;
import mint.soda.enums.NotificationStatusEnum;
import mint.soda.enums.StringEnum;
import mint.soda.service.DownloadService;
import mint.soda.util.other.ConfigUtil;
import mint.soda.util.other.ItemsUtil;
import mint.soda.util.other.RenameUtil;
import mint.soda.util.other.NotificationStatusUtil;
import mint.soda.util.other.TemplateVariables;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import wushuo.tmdb.api.entity.Tmdb;

import java.util.List;
import java.util.Optional;

public interface BaseNotification {

    /**
     * 测试
     *
     * @param notificationConfig     通知配置
     * @param ani                    订阅
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     */
    void test(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum notificationStatusEnum);

    /**
     * 发送通知
     *
     * @param notificationConfig     通知配置
     * @param ani                    订阅
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     * @return 是否成功
     */
    Boolean send(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum notificationStatusEnum);

    /**
     * 替换通知模版
     *
     * @param ani                    订阅
     * @param notificationConfig     通知设置
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     * @return 替换后的通知模版
     */
    default String replaceNotificationTemplate(Ani ani, NotificationConfig notificationConfig, String text, NotificationStatusEnum notificationStatusEnum) {
        String notificationTemplate = Optional.ofNullable(getSelectedTemplate(notificationConfig))
                .filter(template -> !"JSON".equalsIgnoreCase(template.getType()))
                .map(template -> StrUtil.blankToDefault(template.getContent(), template.getTelegramTemplate()))
                .filter(StrUtil::isNotBlank)
                .orElse(notificationConfig.getNotificationTemplate());

        String comment = Opt.ofNullable(notificationConfig)
                .map(NotificationConfig::getComment)
                .filter(StrUtil::isNotBlank)
                .orElse("无备注");

        notificationTemplate = notificationTemplate.replace("${comment}", comment);

        return replaceNotificationTemplate(ani, notificationTemplate, text, notificationStatusEnum);
    }

    static NotificationTemplate getSelectedTemplate(NotificationConfig notificationConfig) {
        String selectedId = notificationConfig == null ? null : notificationConfig.getNotificationTemplateId();
        if (StrUtil.isBlank(selectedId)) return null;
        return Optional.ofNullable(ConfigUtil.CONFIG.getNotificationTemplateList()).orElseGet(List::of)
                .stream()
                .filter(template -> selectedId.equals(template.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 替换通知模版
     *
     * @param ani                    订阅
     * @param notificationTemplate   通知模版
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     * @return 替换后的通知模版
     */
    default String replaceNotificationTemplate(Ani ani, String notificationTemplate, String text, NotificationStatusEnum notificationStatusEnum) {
        notificationTemplate = notificationTemplate.replace("${text}", text);

        // 集数
        double episode = 1.0;
        if (ReUtil.contains(StringEnum.SEASON_REG, text)) {
            episode = Double.parseDouble(ReUtil.get(StringEnum.SEASON_REG, text, 2));
        }

        String episodeFormat = String.format("%02d", (int) episode);

        // x.5
        if (ItemsUtil.is5(episode)) {
            episodeFormat = episodeFormat + ".5";
        }

        notificationTemplate = notificationTemplate.replace("${episode}",
                NumberFormatUtils.format(episode, 1, 0)
        );
        notificationTemplate = notificationTemplate.replace("${episodeFormat}", episodeFormat);


        List<Func1<Ani, Object>> list = List.of(
                Ani::getScore,
                Ani::getThemoviedbName,
                Ani::getBgmUrl,
                Ani::getCurrentEpisodeNumber,
                Ani::getTotalEpisodeNumber
        );

        // 标题、字幕组、季、TMDB 与日期由统一模板变量处理。
        notificationTemplate = TemplateVariables.from(ani).apply(notificationTemplate);

        notificationTemplate = RenameUtil.replaceField(notificationTemplate, ani, list);

        String tmdbId = Opt.ofNullable(ani)
                .map(Ani::getTmdb)
                .map(Tmdb::getId)
                .filter(StrUtil::isNotBlank)
                .orElse("");

        String tmdbUrl = "";
        if (StrUtil.isNotBlank(tmdbId)) {
            Boolean ova = Opt.ofNullable(ani)
                    .map(Ani::getOva)
                    .orElse(false);
            String type = ova ? "movie" : "tv";
            tmdbUrl = StrFormatter.format("https://www.themoviedb.org/{}/{}", type, tmdbId);
        }
        notificationTemplate = notificationTemplate.replace("${tmdburl}", tmdbUrl);

        // 表情与状态名称允许在设置中覆盖。
        String emoji = NotificationStatusUtil.emoji(notificationStatusEnum);
        String action = NotificationStatusUtil.label(notificationStatusEnum);

        notificationTemplate = notificationTemplate.replace("${emoji}", emoji);
        notificationTemplate = notificationTemplate.replace("${action}", action);

        DownloadService downloadService = SpringUtil.getBean(DownloadService.class);
        String downloadPath = downloadService.getDownloadPath(ani);
        notificationTemplate = notificationTemplate.replace("${downloadPath}", downloadPath);

        notificationTemplate = RenameUtil.replaceEpisodeTitle(notificationTemplate, episode, ani);

        return notificationTemplate.trim();
    }
}
