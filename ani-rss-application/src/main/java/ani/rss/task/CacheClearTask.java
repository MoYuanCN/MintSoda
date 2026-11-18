package ani.rss.task;

import ani.rss.commons.FileUtils;
import ani.rss.entity.Config;
import ani.rss.service.ClearService;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.thread.ThreadUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 按配置的间隔自动清理未被引用的缓存。
 */
@Slf4j
@Component
public class CacheClearTask implements BaseTask {

    @Resource
    private ClearService clearService;

    @Override
    public void accept(AtomicBoolean loop) {
        Config config = ConfigUtil.CONFIG;
        Boolean enabled = config.getCacheAutoClear();
        int hours = Math.max(1, config.getCacheAutoClearHours());

        if (!Boolean.TRUE.equals(enabled)) {
            ThreadUtil.sleep(10, TimeUnit.MINUTES);
            return;
        }

        long now = System.currentTimeMillis();
        long last = config.getCacheLastClearTime() == null ? 0L : config.getCacheLastClearTime();
        long due = TimeUnit.HOURS.toMillis(hours);
        if (now - last < due) {
            ThreadUtil.sleep(10, TimeUnit.MINUTES);
            return;
        }

        try {
            long size = clearService.clearCover();
            config.setCacheLastClearTime(now);
            ConfigUtil.sync();
            log.info("自动清理缓存完成，释放 {}", FileUtils.formatSize(size, true));
        } catch (Exception e) {
            log.error("自动清理缓存失败: {}", e.getMessage(), e);
        }

        ThreadUtil.sleep(10, TimeUnit.MINUTES);
    }
}
