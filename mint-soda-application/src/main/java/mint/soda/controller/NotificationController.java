package mint.soda.controller;

import mint.soda.annotation.Auth;
import mint.soda.entity.Ani;
import mint.soda.entity.BgmInfo;
import mint.soda.entity.NotificationConfig;
import mint.soda.entity.NotificationTemplate;
import mint.soda.entity.web.Result;
import mint.soda.enums.NotificationStatusEnum;
import mint.soda.enums.NotificationTypeEnum;
import mint.soda.notification.BaseNotification;
import mint.soda.notification.TelegramNotification;
import mint.soda.util.other.AniUtil;
import mint.soda.util.other.BgmUtil;
import mint.soda.util.other.NotificationUtil;
import mint.soda.util.other.TmdbUtils;
import mint.soda.util.other.ConfigUtil;
import cn.hutool.core.util.ReflectUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import wushuo.tmdb.api.entity.Tmdb;

import java.util.List;
import java.util.Optional;

@RestController
public class NotificationController extends BaseController {

    @Auth
    @Operation(summary = "测试通知")
    @PostMapping("/testNotification")
    public Result<Void> testNotification(@RequestBody NotificationConfig notificationConfig) {
        NotificationTypeEnum notificationType = notificationConfig.getNotificationType();
        Class<? extends BaseNotification> aClass = NotificationUtil.NOTIFICATION_MAP.get(notificationType);
        if (aClass == null) {
            throw new IllegalArgumentException("不支持的通知类型");
        }
        BaseNotification baseNotification = ReflectUtil.newInstance(aClass);
        Ani ani = AniUtil.createAni();
        BgmInfo bgmInfo = BgmUtil.getBgmInfo("292970", true);
        BgmUtil.toAni(bgmInfo, ani);

        String image = ani.getImage();
        String title = ani.getTitle();

        ani.setCover(AniUtil.saveCover(image))
                .setCurrentEpisodeNumber(6)
                .setTotalEpisodeNumber(12)
                .setSubgroup("未知字幕组");

        Optional<Tmdb> tmdb = TmdbUtils.getTmdbTv(title);

        tmdb.ifPresent(ani::setTmdb);

        try {
            baseNotification.test(notificationConfig, ani, "test", NotificationStatusEnum.DOWNLOAD_START);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Auth
    @Operation(summary = "新的通知")
    @PostMapping("/newNotification")
    public Result<NotificationConfig> newNotification() {
        NotificationConfig notificationConfig = NotificationConfig.createNotificationConfig();
        ConfigUtil.CONFIG.getNotificationTemplateList().stream()
                .filter(template -> "default".equals(template.getId()))
                .map(NotificationTemplate::getId)
                .findFirst()
                .ifPresent(notificationConfig::setNotificationTemplateId);
        return Result.success(notificationConfig);
    }

    @Auth
    @Operation(summary = "获取TG最近消息")
    @PostMapping("/getTgUpdates")
    public Result<List<TelegramNotification.Message.Chat>> getUpdates(@RequestBody NotificationConfig notificationConfig) {
        List<TelegramNotification.Message.Chat> map = TelegramNotification.getUpdates(notificationConfig);
        return Result.success(map);
    }

}
