package mint.soda.task;

import mint.soda.entity.Ani;
import mint.soda.entity.BgmInfo;
import mint.soda.entity.Config;
import mint.soda.service.AniService;
import mint.soda.util.other.AniUtil;
import mint.soda.util.other.BgmUtil;
import mint.soda.util.other.ConfigUtil;
import cn.hutool.core.thread.ThreadUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于更新BGM评分
 */
@Slf4j
@Component
public class BgmTask implements BaseTask {

    @Resource
    private AniService aniService;

    @Override
    public void accept(AtomicBoolean loop) {
        try {
            BgmUtil.refreshToken();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        List<Ani> aniList = AniUtil.ANI_LIST;
        for (Ani ani : aniList) {
            if (!loop.get()) {
                return;
            }
            Boolean enable = ani.getEnable();
            if (!enable) {
                continue;
            }
            BgmInfo bgmInfo;
            try {
                bgmInfo = BgmUtil.getBgmInfo(ani);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                continue;
            }

            double score = Optional.ofNullable(bgmInfo.getRating())
                    .map(BgmInfo.Rating::getScore)
                    .orElse(0.0);
            ani.setScore(score);

            Config config = ConfigUtil.CONFIG;
            Boolean updateTotalEpisodeNumber = config.getUpdateTotalEpisodeNumber();
            Boolean forceUpdateTotalEpisodeNumber = config.getForceUpdateTotalEpisodeNumber();

            if (!updateTotalEpisodeNumber) {
                // 未开启更新总集数
                continue;
            }

            aniService.updateTotalEpisodeNumber(ani, bgmInfo, forceUpdateTotalEpisodeNumber);
        }
        AniUtil.sync();

        ThreadUtil.sleep(12, TimeUnit.HOURS);
    }
}
