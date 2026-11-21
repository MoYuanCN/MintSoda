package mint.soda.task;

import mint.soda.commons.ExceptionUtils;
import mint.soda.entity.Config;
import mint.soda.entity.torrent.TorrentsInfo;
import mint.soda.service.DownloadService;
import mint.soda.util.other.ConfigUtil;
import mint.soda.util.other.TorrentUtil;
import cn.hutool.core.thread.ThreadUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 重命名
 */
@Slf4j
@Component
public class RenameTask implements BaseTask {
    @Resource
    private DownloadService downloadService;

    @Override
    public void accept(AtomicBoolean loop) {
        Config config = ConfigUtil.CONFIG;
        int renameSleepSeconds = config.getRenameSleepSeconds();

        if (!TorrentUtil.login()) {
            ThreadUtil.sleep(renameSleepSeconds * 1000L);
            return;
        }
        DownloadService.beginPass();
        try {
            List<TorrentsInfo> torrentsInfos = TorrentUtil.getTorrentsInfos();
            for (TorrentsInfo torrentsInfo : torrentsInfos) {
                if (!loop.get()) {
                    return;
                }
                Boolean deleteStandbyRSSOnly = config.getDeleteStandbyRSSOnly();
                try {
                    TorrentUtil.rename(torrentsInfo);
                    downloadService.notification(torrentsInfo);
                    if (deleteStandbyRSSOnly) {
                        continue;
                    }
                    TorrentUtil.delete(torrentsInfo);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            String message = ExceptionUtils.getMessage(e);
            log.error(message, e);
        } finally {
            DownloadService.endPass();
        }
        ThreadUtil.sleep(renameSleepSeconds * 1000L);
    }
}
