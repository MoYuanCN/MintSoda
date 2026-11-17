package ani.rss.task;

import ani.rss.commons.ExceptionUtils;
import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.service.DownloadService;
import ani.rss.util.other.AniUtil;
import ani.rss.util.other.ConfigUtil;
import ani.rss.util.other.ItemsUtil;
import ani.rss.util.other.TorrentUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * RSS
 */
@Slf4j
@Component
public class RssTask implements BaseTask {
    public static final List<Ani> ANI_LIST = AniUtil.ANI_LIST;
    public static final AtomicBoolean DOWNLOAD_LOCK = new AtomicBoolean(false);
    public static AtomicBoolean LOOP = new AtomicBoolean(false);

    /**
     * 单轮刷新中并发预取 RSS 的默认最大连接数。
     */
    private static final int DEFAULT_RSS_PREFETCH_CONCURRENCY = 4;

    /**
     * 两次轮次之间的最小停顿，防止短间隔且处理超时时紧密循环。
     */
    private static final long MIN_INTERVAL_WAIT = 1000L;

    public static void syncDownload() {
        syncDownload(ANI_LIST);
    }

    public static void syncDownload(List<Ani> aniList) {
        syncLock(lock -> lock.set(true));
        try {
            download(aniList);
        } catch (Exception e) {
            String message = ExceptionUtils.getMessage(e);
            log.error(message, e);
        } finally {
            DOWNLOAD_LOCK.set(false);
        }
    }

    public static void syncLock() {
        syncLock(null);
    }

    public static void syncLock(Consumer<AtomicBoolean> consumer) {
        synchronized (DOWNLOAD_LOCK) {
            if (DOWNLOAD_LOCK.get()) {
                throw new IllegalStateException("存在未完成任务，请等待...");
            }
            if (Objects.nonNull(consumer)) {
                consumer.accept(DOWNLOAD_LOCK);
            }
        }
    }

    public static void download(List<Ani> aniList) {
        DownloadService downloadService = SpringUtil.getBean(DownloadService.class);
        if (!TorrentUtil.login()) {
            return;
        }
        ItemsUtil.beginRssBatch();
        TorrentUtil.beginPass();
        DownloadService.beginPass();
        try {
            // 先并发拉取 RSS，后续逐个订阅只做本地解析，避免串行等待。
            Integer concurrency = ConfigUtil.CONFIG.getRssConcurrency();
            ItemsUtil.prefetchRss(aniList,
                    concurrency == null ? DEFAULT_RSS_PREFETCH_CONCURRENCY : concurrency);
            for (Ani ani : aniList) {
                if (!LOOP.get()) {
                    // 停止循环
                    return;
                }

                if (!ANI_LIST.contains(ani)) {
                    // 订阅可能已经被删除
                    continue;
                }

                String title = ani.getTitle();
                Boolean enable = ani.getEnable();
                if (!enable) {
                    log.debug("{} 未启用", title);
                    continue;
                }

                try {
                    downloadService.downloadAni(ani);
                } catch (Exception e) {
                    String message = ExceptionUtils.getMessage(e);
                    log.error("{} {}", title, message);
                    log.error(message, e);
                }
            }
        } finally {
            DownloadService.endPass();
            TorrentUtil.endPass();
            ItemsUtil.endRssBatch();
        }
    }

    @Override
    public void accept(AtomicBoolean loop) {
        Config config = ConfigUtil.CONFIG;
        Integer sleep = config.getRssSleepSeconds();

        if (!config.getRss()) {
            log.debug("rss未启用");
            ThreadUtil.sleep(sleep, TimeUnit.SECONDS);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            LOOP = loop;
            syncDownload(ANI_LIST);
        } catch (Exception e) {
            String message = ExceptionUtils.getMessage(e);
            log.error(message, e);
        }
        // 以固定间隔轮询：扣除本轮耗时，避免长任务把实际间隔越拖越长。
        long elapsed = System.currentTimeMillis() - start;
        long wait = Math.max(MIN_INTERVAL_WAIT, sleep * 1000L - elapsed);
        if (elapsed >= sleep * 1000L) {
            // 本轮耗时超过间隔时短暂让步，避免短间隔下连续满载运行。
            log.debug("本轮 RSS 刷新耗时 {} ms，已超过设定间隔", elapsed);
        }
        ThreadUtil.sleep(wait, TimeUnit.MILLISECONDS);
    }
}
