package ani.rss.service.support;

import ani.rss.entity.Ani;
import ani.rss.entity.StandbyRss;
import ani.rss.entity.torrent.TorrentsInfo;
import ani.rss.enums.NotificationStatusEnum;
import ani.rss.enums.TorrentsTagEnum;
import ani.rss.plugin.PluginHook;
import ani.rss.plugin.PluginHooks;
import ani.rss.util.other.AniUtil;
import ani.rss.util.other.ConfigUtil;
import ani.rss.util.other.NotificationUtil;
import ani.rss.util.other.TorrentUtil;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 下载完成通知与完结迁移。
 * <p>
 * 从下载服务中拆出：下载服务只负责“把种子交给下载器”，完成通知、字幕组反查与
 * 完结后迁移属于独立的收尾流程，改动频率与触发时机都不同。
 */
@Slf4j
public final class DownloadNotifier {

    private DownloadNotifier() {
    }

    /**
     * 处理下载完成：打标签防重复、推断字幕组、发送通知并执行完结迁移。
     *
     * @param torrentsInfo 种子信息
     * @param resolver     由下载路径反查订阅
     */
    public static void completed(TorrentsInfo torrentsInfo, Function<TorrentsInfo, Optional<Ani>> resolver) {
        if (!Boolean.TRUE.equals(torrentsInfo.finished())) {
            return;
        }
        List<String> tags = torrentsInfo.getTagList();
        // 完成标签是幂等标记，重复通知会打扰用户。
        if (tags.contains(TorrentsTagEnum.DOWNLOAD_COMPLETE.getValue())) {
            return;
        }
        if (!Boolean.TRUE.equals(TorrentUtil.addTags(torrentsInfo, TorrentsTagEnum.DOWNLOAD_COMPLETE.getValue()))) {
            return;
        }

        Optional<Ani> aniOpt = resolver.apply(torrentsInfo);
        if (aniOpt.isEmpty()) {
            log.debug("未能获取番剧对象: {}", torrentsInfo.getName());
            return;
        }
        Ani ani = aniOpt.get();

        String subgroup = resolveSubgroup(ani, tags);
        ani.setSubgroup(subgroup);

        String text = StrFormatter.format("{} 下载完成", torrentsInfo.getName());
        if (tags.contains(TorrentsTagEnum.STANDBY_RSS.getValue())) {
            text = StrFormatter.format("(备用RSS) {}", text);
        }
        NotificationUtil.send(ConfigUtil.CONFIG, ani, text, NotificationStatusEnum.DOWNLOAD_END);

        PluginHooks.downloadEvent(PluginHook.DownloadEvent.COMPLETED, ani,
                PluginHooks.downloadSnapshot(ani,
                        StrUtil.nullToEmpty(torrentsInfo.getHash()),
                        torrentsInfo.getName(), torrentsInfo.getName(), subgroup,
                        null, StrUtil.nullToEmpty(torrentsInfo.getSavePath()), tags, null));

        try {
            AniUtil.completed(ani);
        } catch (Exception e) {
            log.error("番剧完结迁移失败 {}", ani.getTitle());
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 备用 RSS 下载的任务用字幕组标签标识来源，标签命中时以标签为准。
     */
    private static String resolveSubgroup(Ani ani, List<String> tags) {
        Set<String> labels = ani.getStandbyRssList()
                .stream()
                .map(StandbyRss::getLabel)
                .collect(Collectors.toSet());
        String subgroup = tags.stream()
                .filter(labels::contains)
                .findFirst()
                .orElse(ani.getSubgroup());
        return StrUtil.blankToDefault(subgroup, "未知字幕组");
    }
}
