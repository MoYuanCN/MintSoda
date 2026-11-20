package ani.rss.service;

import ani.rss.commons.ExceptionUtils;
import ani.rss.commons.FileUtils;
import ani.rss.commons.GsonStatic;
import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.entity.Item;
import ani.rss.entity.torrent.TorrentsInfo;
import ani.rss.enums.NotificationStatusEnum;
import ani.rss.plugin.PluginHook;
import ani.rss.service.support.DownloadNotifier;
import ani.rss.service.support.DownloadPass;
import ani.rss.service.support.DownloadPathResolver;
import ani.rss.plugin.PluginHooks;
import ani.rss.enums.StringEnum;
import ani.rss.enums.TorrentsStateEnum;
import ani.rss.enums.TorrentsTagEnum;
import ani.rss.util.other.*;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * 下载的主要逻辑
 */
@Slf4j
@Service
public class DownloadService {
    private static final Config CONFIG = ConfigUtil.CONFIG;
    private static final Object LOCK = new Object();

    /**
     * 下载动漫
     *
     * @param ani 订阅
     */
    /**
     * 开始一轮处理，建立本轮缓存。委托给 {@link DownloadPass}，保留旧入口以兼容调用方。
     */
    public static void beginPass() {
        DownloadPass.begin();
    }

    /**
     * 结束一轮处理，释放本轮缓存。
     */
    public static void endPass() {
        DownloadPass.end();
    }

    @Synchronized("LOCK")
    public void downloadAni(Ani ani) {
        // 单条刷新（如添加订阅后立即下载）没有外层轮次时，自行建立并释放本次缓存。
        boolean ownsPass = !DownloadPass.active();
        if (ownsPass) {
            beginPass();
        }
        try {
            downloadAniInternal(ani);
        } finally {
            if (ownsPass) {
                endPass();
            }
        }
    }

    private void downloadAniInternal(Ani ani) {
        Boolean delete = Boolean.TRUE.equals(CONFIG.getDelete());
        Boolean autoDisabled = Boolean.TRUE.equals(CONFIG.getAutoDisabled());
        Integer downloadCount = ObjectUtil.defaultIfNull(CONFIG.getDownloadCount(), 0);
        Integer delayedDownload = ObjectUtil.defaultIfNull(CONFIG.getDelayedDownload(), 0);
        Boolean deleteStandbyRSSOnly = CONFIG.getDeleteStandbyRSSOnly();

        String title = ani.getTitle();
        Integer season = ani.getSeason();
        Boolean downloadNew = Boolean.TRUE.equals(ani.getDownloadNew());
        // 旧配置或导入的订阅可能没有该字段，缺省视为没有禁止下载的集数。
        List<Double> notDownload = ObjectUtil.defaultIfNull(ani.getNotDownload(), List.of());

        List<TorrentsInfo> torrentsInfos = TorrentUtil.getTorrentsInfos();

        int currentDownloadCount = 0;
        List<Item> items = ItemsUtil.getItems(ani);

        ItemsUtil.omit(ani, items);
        log.debug("{} 共 {} 个", title, items.size());

        // 统计正在下载任务数量 计算同时下载数量限制
        long count = torrentsInfos
                .stream()
                .filter(it -> {
                    // 未下载完成
                    return !it.finished();
                })
                .count();

        String savePath = getDownloadPath(ani);

        // 实时保存文件
        boolean sync = false;

        // 同一订阅的任务只取一次，后续按名称/hash 匹配都在本地集合中完成。
        List<TorrentsInfo> aniTorrents = new ArrayList<>(
                TorrentUtil.findTorrentsInfosByAni(ani, torrentsInfos));
        Set<String> torrentHashes = new HashSet<>();
        for (TorrentsInfo torrentsInfo : torrentsInfos) {
            torrentHashes.add(StrUtil.nullToEmpty(torrentsInfo.getHash()).toLowerCase());
        }

        for (Item item : items) {
            // 序列化整条资源开销很大，仅在开启 debug 日志时才格式化。
            if (log.isDebugEnabled()) {
                log.debug(JSONUtil.formatJsonStr(GsonStatic.toJson(item)));
            }
            if (!PluginHooks.accepts(ani, item)) {
                log.debug("插件已过滤 {}", item.getReName());
                continue;
            }
            String reName = item.getReName();
            File torrent = TorrentUtil.getTorrent(ani, item);
            boolean master = item.getMaster();
            String hash = FileUtil.mainName(torrent)
                    .trim().toLowerCase();

            Double episode = item.getEpisode();
            // .5 集
            boolean is5 = ItemsUtil.is5(episode);

            // 已经下载过
            if (torrent.exists()) {
                log.debug("种子记录已存在 {}", reName);
                if (master && !is5) {
                    currentDownloadCount++;
                }
                continue;
            }

            if (notDownload.contains(episode)) {
                if (master && !is5) {
                    currentDownloadCount++;
                }
                log.debug("已被禁止下载: {}", reName);
                continue;
            }

            // 只下载最新集
            if (downloadNew) {
                Item newItem = items.get(items.size() - 1);

                // 日期一致也可下载, 防止字幕组同时发多集
                Date pubDate = item.getPubDate();
                Date newPubDate = newItem.getPubDate();
                if (Objects.nonNull(pubDate) && Objects.nonNull(newPubDate)) {
                    String pubDateFormat = DateUtil.format(pubDate, "yyyy-MM-dd");
                    String newPubDateFormat = DateUtil.format(newPubDate, "yyyy-MM-dd");
                    // 日期不一致则跳过
                    if (!pubDateFormat.equals(newPubDateFormat)) {
                        if (master && !is5) {
                            currentDownloadCount++;
                        }
                        continue;
                    }
                } else if (item != newItem) {
                    if (master && !is5) {
                        currentDownloadCount++;
                    }
                    continue;
                }
            }

            Date pubDate = item.getPubDate();
            if (Objects.nonNull(pubDate) && delayedDownload > 0) {
                Date now = DateUtil.offset(new Date(), DateField.MINUTE, -delayedDownload);
                if (now.getTime() < pubDate.getTime()) {
                    log.info("延迟下载 {}", reName);
                    continue;
                }
            }

            // 仅在主RSS更新后删除备用RSS
            if (delete && master && deleteStandbyRSSOnly) {
                TorrentsInfo standbyRSS = aniTorrents
                        .stream()
                        .filter(torrentsInfo -> {
                            if (!torrentsInfo.getSavePath().equals(savePath)) {
                                return false;
                            }
                            if (!ReUtil.contains(StringEnum.SEASON_REG, torrentsInfo.getName())) {
                                return false;
                            }
                            String s = ReUtil.get(StringEnum.SEASON_REG, torrentsInfo.getName(), 0);
                            if (!s.equals(ReUtil.get(StringEnum.SEASON_REG, reName, 0))) {
                                return false;
                            }
                            List<String> tags = torrentsInfo.getTagList();
                            // 包含 备用RSS 标签或者 无主RSS字幕组信息
                            return tags.contains(TorrentsTagEnum.STANDBY_RSS.getValue()) ||
                                    !tags.contains(ani.getSubgroup());
                        })
                        .findFirst()
                        .orElse(null);

                if (Objects.nonNull(standbyRSS)) {
                    List<String> tags = standbyRSS.getTagList();
                    if (!tags.contains(TorrentsTagEnum.RENAME.getValue())) {
                        // 未完成重命名
                        continue;
                    }
                    if (!TorrentUtil.delete(standbyRSS)) {
                        log.debug("备用RSS可能还未做种完成 {}", standbyRSS.getName());
                        // 删除失败或者不允许删除
                        continue;
                    }
                    torrentsInfos.remove(standbyRSS);
                    // 同步移除，避免同一订阅的后续条目再次命中已删除的任务。
                    aniTorrents.remove(standbyRSS);
                    torrentHashes.remove(StrUtil.nullToEmpty(standbyRSS.getHash()).toLowerCase());
                }
            }

            // 已经下载过
            if (torrentHashes.contains(hash)) {
                log.info("已有下载任务 hash:{} name:{}", hash, reName);
                if (master && !is5) {
                    currentDownloadCount++;
                }
                continue;
            }

            // 未开启rename不进行检测
            if (itemDownloaded(ani, item, aniTorrents)) {
                log.info("本地文件已存在 {}", reName);
                if (master && !is5) {
                    currentDownloadCount++;
                }
                continue;
            }

            // 同时下载数量限制
            if (downloadCount > 0) {
                if (count >= downloadCount) {
                    log.debug("达到同时下载数量限制 {}", downloadCount);
                    continue;
                }
            }

            File saveTorrent = TorrentUtil.saveTorrent(ani, item);

            if (!saveTorrent.exists()) {
                // 种子下载失败
                continue;
            }

            deleteStandbyRss(ani, item, aniTorrents, torrentHashes);

            if (!AniUtil.contains(ani)) {
                return;
            }

            sync = true;

            download(ani, item, savePath, saveTorrent);
            torrentHashes.add(hash);

            if (master && !is5) {
                currentDownloadCount++;
            }
            count++;
        }

        if (sync) {
            int currentEpisodeNumber = ItemsUtil.currentEpisodeNumber(ani, items);
            // 更新当前集数
            ani.setCurrentEpisodeNumber(currentEpisodeNumber);
            // 更新下载时间
            ani.setLastDownloadTime(System.currentTimeMillis());
            AniUtil.sync();
        }

        if (!autoDisabled) {
            return;
        }
        int totalEpisodeNumber = ani.getTotalEpisodeNumber();
        if (totalEpisodeNumber < 1) {
            return;
        }
        if (currentDownloadCount >= totalEpisodeNumber) {
            log.info("{} 第 {} 季 共 {} 集 已全部下载完成, 自动停止订阅", title, season, totalEpisodeNumber);
            NotificationUtil.send(CONFIG, ani, StrFormatter.format("{} 订阅已完结", title), NotificationStatusEnum.COMPLETED);
            ani.setEnable(false);
            AniUtil.sync();
        }
    }

    /**
     * 删除备用rss
     *
     * @param ani           订阅
     * @param item          资源项
     * @param torrentsInfos 该订阅对应的任务列表
     * @param torrentHashes 本轮已存在任务的 hash 集合，删除任务后同步移除
     */
    private void deleteStandbyRss(Ani ani, Item item, List<TorrentsInfo> torrentsInfos,
                                  Set<String> torrentHashes) {
        Boolean standbyRss = CONFIG.getStandbyRss();
        Boolean coexist = CONFIG.getCoexist();
        Boolean delete = CONFIG.getDelete();
        String reName = item.getReName();

        if (!delete) {
            return;
        }

        if (!standbyRss) {
            return;
        }

        if (coexist) {
            // 开启多字幕组共存将不会进行洗版
            return;
        }

        if (!ReUtil.contains(StringEnum.SEASON_REG, reName)) {
            return;
        }

        String episode = ReUtil.get(StringEnum.SEASON_REG, reName, 0);

        String downloadPath = getDownloadPath(ani);

        // 删除备用rss任务
        for (TorrentsInfo torrentsInfo : torrentsInfos) {
            String name = torrentsInfo.getName();
            if (!ReUtil.contains(StringEnum.SEASON_REG, name)) {
                continue;
            }
            String s = ReUtil.get(StringEnum.SEASON_REG, name, 0);
            if (s.equalsIgnoreCase(episode)) {
                TorrentUtil.delete(torrentsInfo, true, true);
                torrentHashes.remove(StrUtil.nullToEmpty(torrentsInfo.getHash()).toLowerCase());
            }
        }

        List<File> files = DownloadPass.listFiles(downloadPath);
        boolean changed = false;
        for (File file : files) {
            String fileName = file.getName();
            if (!ReUtil.contains(StringEnum.SEASON_REG, fileName)) {
                continue;
            }
            fileName = ReUtil.get(StringEnum.SEASON_REG, fileName, 0);
            if (!fileName.equalsIgnoreCase(episode)) {
                continue;
            }
            boolean isDel = false;
            // 文件在删除前先判断其格式
            if (file.isFile()) {
                String extName = FileUtil.extName(file);
                // 没有后缀 跳过
                if (StrUtil.isBlank(extName)) {
                    continue;
                }
                if (FileUtils.isVideoFormat(extName)) {
                    isDel = true;
                }
                if (FileUtils.isSubtitleFormat(extName)) {
                    isDel = true;
                }
                if (List.of("nfo", "bif").contains(extName)) {
                    isDel = true;
                }
                if (file.getName().endsWith("-thumb.jpg")) {
                    isDel = true;
                }
            }
            if (file.isDirectory()) {
                isDel = true;
            }
            if (isDel) {
                log.info("已开启备用RSS, 自动删除 {}", file);
                try {
                    FileUtil.del(file);
                    changed = true;
                    log.info("删除成功 {}", file);
                } catch (Exception e) {
                    log.error("删除失败 {}", file);
                    log.error(e.getMessage(), e);
                }
            }
        }
        if (changed) {
            DownloadPass.invalidate(downloadPath);
        }
    }

    /**
     * 下载
     *
     * @param ani         订阅
     * @param item        资源项
     * @param savePath    保存位置
     * @param torrentFile 种子文件
     */
    public void download(Ani ani, Item item, String savePath, File torrentFile) {
        ani = ObjectUtil.clone(ani);

        String name = item.getReName();
        Boolean master = item.getMaster();
        String subgroup = item.getSubgroup();
        if (MatchRuleUtil.isReverseMarker(subgroup)) {
            // 反匹配标记本身不是字幕组，优先使用已保存的命中结果。
            subgroup = MatchRuleUtil.storedSubgroup(ani, item.getEpisode());
        }
        subgroup = StrUtil.blankToDefault(subgroup, "未知字幕组");
        ani.setSubgroup(subgroup);

        log.info("添加下载 {}", name);

        if (!torrentFile.exists()) {
            log.error("种子下载出现问题 {} {}", name, torrentFile);
            return;
        }
        savePath = FileUtils.getAbsolutePath(savePath);

        // 插件可在提交下载器之前调整保存位置、名称，或直接取消本次下载。
        PluginHook.InterceptResult intercept = PluginHooks.intercept(ani, item, savePath, name);
        if (!intercept.allowed()) {
            log.info("插件取消下载 {}", name);
            FileUtil.del(torrentFile);
            return;
        }
        savePath = FileUtils.getAbsolutePath(intercept.savePath());
        item.setReName(intercept.name());
        name = intercept.name();

        String text = StrFormatter.format("{} 已更新", name);
        if (!master) {
            text = StrFormatter.format("(备用RSS) {}", text);
        }
        NotificationUtil.send(CONFIG, ani, text, NotificationStatusEnum.DOWNLOAD_START);

        Integer downloadRetry = CONFIG.getDownloadRetry();
        for (int i = 1; i <= downloadRetry; i++) {
            try {
                if (TorrentUtil.download(ani, item, savePath, torrentFile)) {
                    trackAddedTorrent(item, savePath, torrentFile);
                    PluginHooks.downloadEvent(PluginHook.DownloadEvent.ADDED, ani,
                            PluginHooks.downloadSnapshot(ani,
                                    FileUtil.mainName(torrentFile).trim().toLowerCase(),
                                    item.getReName(), item.getTitle(), item.getSubgroup(),
                                    item.getEpisode(), savePath, List.of(item.getSubgroup()),
                                    item.getLength()));
                    return;
                }
            } catch (Exception e) {
                String message = ExceptionUtils.getMessage(e);
                log.error(message, e);
            }
            log.error("{} 下载失败将进行重试, 当前重试次数为{}次", name, i);
        }

        // 删除下载失败的种子, 下次轮询仍会重试
        FileUtil.del(torrentFile);

        log.error("{} 添加失败，疑似为坏种", name);
        NotificationUtil.send(CONFIG, ani,
                StrFormatter.format("{} 添加失败，疑似为坏种", name),
                NotificationStatusEnum.ERROR);
        PluginHooks.downloadEvent(PluginHook.DownloadEvent.FAILED, ani,
                PluginHooks.downloadSnapshot(ani,
                        FileUtil.mainName(torrentFile).trim().toLowerCase(),
                        name, item.getTitle(), item.getSubgroup(), item.getEpisode(),
                        savePath, List.of(item.getSubgroup()), item.getLength()));
    }

    /**
     * 下载完成通知。收尾逻辑见 {@link DownloadNotifier}。
     *
     * @param torrentsInfo 种子信息
     */
    public void notification(TorrentsInfo torrentsInfo) {
        DownloadNotifier.completed(torrentsInfo, this::findAniByDownloadPath);
    }

    /**
     * 获取下载位置。模板解析已抽到 {@link DownloadPathResolver}，此处保留原有入口。
     *
     * @param ani 订阅
     * @return 下载位置
     */
    public String getDownloadPath(Ani ani) {
        return DownloadPathResolver.resolve(ani);
    }

    /**
     * 使用指定模板获取下载位置，不修改订阅本身。
     *
     * @param ani                  订阅
     * @param downloadPathTemplate 下载位置模板
     * @return 下载位置
     */
    public String getDownloadPath(Ani ani, String downloadPathTemplate) {
        return DownloadPathResolver.resolve(ani, downloadPathTemplate);
    }

    /**
     * 使用指定配置获取下载位置。
     *
     * @param ani    订阅
     * @param config 配置
     * @return 下载位置
     */
    public String getDownloadPath(Ani ani, Config config) {
        return DownloadPathResolver.resolve(ani, config);
    }

    /**
     * 将本轮新增的任务登记到刷新缓存，使同时下载限制在后续订阅中仍然准确。
     *
     * @param item        资源项
     * @param savePath    保存位置
     * @param torrentFile 种子文件
     */
    private void trackAddedTorrent(Item item, String savePath, File torrentFile) {
        String hash = FileUtil.mainName(torrentFile).trim().toLowerCase();
        TorrentUtil.trackTorrent(new TorrentsInfo()
                .setHash(hash)
                .setName(item.getReName())
                .setSavePath(savePath)
                .setState(TorrentsStateEnum.downloading)
                .setProgress(0.0));
    }

    /**
     * 判断是否已经下载过
     *
     * @param ani          订阅
     * @param item         资源项
     * @param downloadList 下载列表
     * @return 是否已下载
     */
    public Boolean itemDownloaded(Ani ani, Item item, Boolean downloadList) {
        return itemDownloaded(ani, item, downloadList
                ? TorrentUtil.findTorrentsInfosByAni(ani)
                : List.of());
    }

    private Boolean itemDownloaded(Ani ani, Item item, List<TorrentsInfo> aniTorrents) {
        Boolean rename = CONFIG.getRename();
        if (!rename) {
            return false;
        }

        String downloadPathTemplate = CONFIG.getDownloadPathTemplate();

        if (StrUtil.isBlank(downloadPathTemplate)) {
            return false;
        }

        Boolean fileExist = CONFIG.getFileExist();
        if (!fileExist) {
            return false;
        }

        Integer season = ani.getSeason();
        Boolean ova = ani.getOva();
        String reName = item.getReName();
        Double episode = item.getEpisode();

        for (TorrentsInfo torrentsInfo : aniTorrents) {
            String name = torrentsInfo.getName();
            if (!name.equalsIgnoreCase(reName)) {
                continue;
            }
            log.info("已存在下载任务 {}", reName);
            TorrentUtil.saveTorrent(ani, item);
            return true;
        }

        String downloadPath = getDownloadPath(ani);
        List<File> files = DownloadPass.listFiles(downloadPath);

        if (files.stream()
                .filter(file -> FileUtils.isVideoFormat(file.getName()))
                .anyMatch(file -> {
                    if (ova) {
                        return true;
                    }

                    String fileName = file.getName();
                    if (!ReUtil.contains(StringEnum.SEASON_REG, fileName)) {
                        return false;
                    }

                    String seasonStr = ReUtil.get(StringEnum.SEASON_REG, fileName, 1);

                    String episodeStr = ReUtil.get(StringEnum.SEASON_REG, fileName, 2);

                    if (StrUtil.isBlank(seasonStr) || StrUtil.isBlank(episodeStr)) {
                        return false;
                    }

                    return season == Integer.parseInt(seasonStr) && episode == Double.parseDouble(episodeStr);
                })) {
            // 保存 torrent 下次只校验 torrent 是否存在 ， 可以将config设置到固态硬盘，防止一直唤醒机械硬盘
            TorrentUtil.saveTorrent(ani, item);
            log.info("本地已存在 {}", reName);
            return true;
        }
        return false;
    }

    /**
     * 根据任务反查订阅
     *
     * @param torrentsInfo 种子信息
     * @return 订阅
     */
    public Optional<Ani> findAniByDownloadPath(TorrentsInfo torrentsInfo) {
        String downloadDir = torrentsInfo.getSavePath();
        return AniUtil.ANI_LIST
                .stream()
                .filter(ani -> {
                    String path = getDownloadPath(ani);
                    return path.equals(downloadDir);
                })
                .map(ObjectUtil::clone)
                .findFirst();
    }

}
