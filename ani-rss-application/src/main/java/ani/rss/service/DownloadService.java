package ani.rss.service;

import ani.rss.commons.ExceptionUtils;
import ani.rss.commons.FileUtils;
import ani.rss.commons.GsonStatic;
import ani.rss.commons.PinyinUtils;
import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.entity.Item;
import ani.rss.entity.StandbyRss;
import ani.rss.entity.torrent.TorrentsInfo;
import ani.rss.enums.NotificationStatusEnum;
import ani.rss.enums.StringEnum;
import ani.rss.enums.TorrentsStateEnum;
import ani.rss.enums.TorrentsTagEnum;
import ani.rss.util.other.*;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.lang.func.Func1;
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
import wushuo.tmdb.api.entity.Tmdb;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 下载的主要逻辑
 */
@Slf4j
@Service
public class DownloadService {
    private static final Config CONFIG = ConfigUtil.CONFIG;
    private static final Object LOCK = new Object();

    /**
     * 单轮刷新内的目录与任务缓存。
     * 短间隔轮询时避免为每个条目重复列目录或请求下载器，减少磁盘唤醒与网络开销。
     */
    private static final class PassCache {
        private final Map<String, List<File>> dirFiles = new HashMap<>();
        private final Map<String, String> downloadPaths = new HashMap<>();
    }

    private static final ThreadLocal<PassCache> PASS_CACHE = new ThreadLocal<>();

    /**
     * 开始一轮处理，建立本轮缓存。
     */
    public static void beginPass() {
        PASS_CACHE.set(new PassCache());
    }

    /**
     * 结束一轮处理，释放本轮缓存。
     */
    public static void endPass() {
        PASS_CACHE.remove();
    }

    /**
     * 列出目录内容，同一轮内相同目录只读取一次。
     *
     * @param path 目录
     * @return 文件列表
     */
    private static List<File> listFilesCached(String path) {
        PassCache cache = PASS_CACHE.get();
        if (cache == null) {
            return FileUtils.listFileList(path);
        }
        return cache.dirFiles.computeIfAbsent(path, FileUtils::listFileList);
    }

    /**
     * 删除文件后使对应目录的缓存失效，避免同一轮内继续使用过期的文件列表。
     *
     * @param path 目录
     */
    private static void invalidateDirCache(String path) {
        PassCache cache = PASS_CACHE.get();
        if (cache != null) {
            cache.dirFiles.remove(path);
        }
    }

    /**
     * 下载动漫
     *
     * @param ani 订阅
     */
    @Synchronized("LOCK")
    public void downloadAni(Ani ani) {
        // 单条刷新（如添加订阅后立即下载）没有外层轮次时，自行建立并释放本次缓存。
        boolean ownsPass = PASS_CACHE.get() == null;
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
        Boolean delete = CONFIG.getDelete();
        Boolean autoDisabled = CONFIG.getAutoDisabled();
        Integer downloadCount = CONFIG.getDownloadCount();
        Integer delayedDownload = CONFIG.getDelayedDownload();
        Boolean deleteStandbyRSSOnly = CONFIG.getDeleteStandbyRSSOnly();

        String title = ani.getTitle();
        Integer season = ani.getSeason();
        Boolean downloadNew = ani.getDownloadNew();
        List<Double> notDownload = ani.getNotDownload();

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

        for (Item item : items) {
            log.debug(JSONUtil.formatJsonStr(GsonStatic.toJson(item)));
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
                TorrentsInfo standbyRSS = torrentsInfos
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
                }
            }

            // 已经下载过
            if (torrentsInfos
                    .stream()
                    .anyMatch(torrentsInfo ->
                            // hash 相同
                            torrentsInfo.getHash().equals(hash))) {
                log.info("已有下载任务 hash:{} name:{}", hash, reName);
                if (master && !is5) {
                    currentDownloadCount++;
                }
                continue;
            }

            // 未开启rename不进行检测
            if (itemDownloaded(ani, item, true)) {
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

            deleteStandbyRss(ani, item);

            if (!AniUtil.ANI_LIST.contains(ani)) {
                return;
            }

            sync = true;

            download(ani, item, savePath, saveTorrent);

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
     * @param ani  订阅
     * @param item 资源项
     */
    public void deleteStandbyRss(Ani ani, Item item) {
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

        List<TorrentsInfo> torrentsInfos = TorrentUtil.findTorrentsInfosByAni(ani);

        // 删除备用rss任务
        for (TorrentsInfo torrentsInfo : torrentsInfos) {
            String name = torrentsInfo.getName();
            if (!ReUtil.contains(StringEnum.SEASON_REG, name)) {
                continue;
            }
            String s = ReUtil.get(StringEnum.SEASON_REG, name, 0);
            if (s.equalsIgnoreCase(episode)) {
                TorrentUtil.delete(torrentsInfo, true, true);
            }
        }

        List<File> files = listFilesCached(downloadPath);
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
            invalidateDirCache(downloadPath);
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
        subgroup = StrUtil.blankToDefault(subgroup, "未知字幕组");
        ani.setSubgroup(subgroup);

        log.info("添加下载 {}", name);

        if (!torrentFile.exists()) {
            log.error("种子下载出现问题 {} {}", name, torrentFile);
            return;
        }
        savePath = FileUtils.getAbsolutePath(savePath);

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
    }

    /**
     * 下载完成通知
     *
     * @param torrentsInfo 种子信息
     */
    public void notification(TorrentsInfo torrentsInfo) {
        Boolean finished = torrentsInfo.finished();
        String name = torrentsInfo.getName();

        if (!finished) {
            return;
        }
        // 添加下载完成标签，防止重复通知
        List<String> tags = torrentsInfo.getTagList();
        if (tags.contains(TorrentsTagEnum.DOWNLOAD_COMPLETE.getValue())) {
            return;
        }
        Boolean b = TorrentUtil.addTags(torrentsInfo, TorrentsTagEnum.DOWNLOAD_COMPLETE.getValue());
        if (!b) {
            return;
        }
        Optional<Ani> aniOpt = findAniByDownloadPath(torrentsInfo);

        if (aniOpt.isEmpty()) {
            log.debug("未能获取番剧对象: {}", torrentsInfo.getName());
            return;
        }

        Ani ani = aniOpt.get();

        // 根据标签反向判断出字幕组
        String subgroup = ani.getSubgroup();
        Set<String> collect = ani.getStandbyRssList()
                .stream()
                .map(StandbyRss::getLabel)
                .collect(Collectors.toSet());

        subgroup = tags
                .stream()
                .filter(collect::contains)
                .findFirst()
                .orElse(subgroup);
        subgroup = StrUtil.blankToDefault(subgroup, "未知字幕组");
        ani.setSubgroup(subgroup);

        String text = StrFormatter.format("{} 下载完成", name);
        if (tags.contains(TorrentsTagEnum.STANDBY_RSS.getValue())) {
            text = StrFormatter.format("(备用RSS) {}", text);
        }
        NotificationUtil.send(CONFIG, ani, text, NotificationStatusEnum.DOWNLOAD_END);

        String title = ani.getTitle();

        try {
            AniUtil.completed(ani);
        } catch (Exception e) {
            log.error("番剧完结迁移失败 {}", title);
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 获取下载位置
     *
     * @param ani 订阅
     * @return 下载位置
     */
    public String getDownloadPath(Ani ani) {
        return getDownloadPath(ani, CONFIG);
    }

    /**
     * 获取下载位置
     *
     * @param ani                  订阅
     * @param downloadPathTemplate 下载位置模板
     * @return 下载位置
     */
    public String getDownloadPath(Ani ani, String downloadPathTemplate) {
        Ani clone = ObjectUtil.clone(ani);
        clone.setCustomDownloadPathTemplate(downloadPathTemplate)
                .setCustomDownloadPath(true);
        return getDownloadPath(clone, CONFIG);
    }

    /**
     * 获取下载位置
     *
     * @param ani 订阅
     * @return 下载位置
     */
    public String getDownloadPath(Ani ani, Config config) {
        PassCache cache = PASS_CACHE.get();
        String cacheKey = null;
        if (cache != null && config == CONFIG) {
            cacheKey = String.join("|",
                    StrUtil.nullToEmpty(ani.getId()),
                    String.valueOf(ani.getSeason()),
                    StrUtil.nullToEmpty(ani.getTitle()),
                    StrUtil.nullToEmpty(ani.getSubgroup()),
                    String.valueOf(ani.getOva()),
                    String.valueOf(ani.getReleaseDate()),
                    String.valueOf(ani.getCustomDownloadPath()),
                    StrUtil.nullToEmpty(ani.getCustomDownloadPathTemplate())
            );
            String cached = cache.downloadPaths.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        Boolean customDownloadPath = ani.getCustomDownloadPath();
        String customDownloadPathTemplate = ani.getCustomDownloadPathTemplate();
        Boolean ova = ani.getOva();

        String downloadPathTemplate = config.getDownloadPathTemplate();
        String ovaDownloadPathTemplate = config.getOvaDownloadPathTemplate();
        if (ova && StrUtil.isNotBlank(ovaDownloadPathTemplate)) {
            // 剧场版位置
            downloadPathTemplate = ovaDownloadPathTemplate;
        }

        if (customDownloadPath && StrUtil.isNotBlank(customDownloadPathTemplate)) {
            // 自定义下载位置
            downloadPathTemplate = StrUtil.split(customDownloadPathTemplate, "\n", true, true)
                    .stream()
                    .map(FileUtils::getAbsolutePath)
                    .findFirst()
                    .orElse(downloadPathTemplate);
        }

        String title = ani.getTitle().trim();

        String letter = PinyinUtils.getPinyinInitialLetters(title);

        downloadPathTemplate = downloadPathTemplate.replace("${letter}", letter);

        Date releaseDate = ani.getReleaseDate();
        Tmdb tmdb = ani.getTmdb();

        Date tmdbDate = Optional.ofNullable(tmdb)
                .map(Tmdb::getDate)
                .orElse(releaseDate);

        int tmdbYear = DateUtil.year(tmdbDate);
        int year = DateUtil.year(releaseDate);
        int month = DateUtil.month(releaseDate) + 1;
        String monthFormat = String.format("%02d", month);

        // 季度
        if (
                downloadPathTemplate.contains("${quarter}") ||
                        downloadPathTemplate.contains("${quarterFormat}") ||
                        downloadPathTemplate.contains("${quarterName}")
        ) {
            int quarter;
            String quarterName;
            /*
            https://github.com/wushuo894/ani-rss/pull/451
            优化季度判断规则，避免将月底先行播放的番归类到上个季度
            */
            if (List.of(12, 1, 2).contains(month)) {
                if (month == 12) {
                    // 当使用季度信息, 并且月份等于12时, 年份自动 +1。避免年份与月份不一致
                    year++;
                }
                quarter = 1;
                quarterName = "冬";
            } else if (List.of(3, 4, 5).contains(month)) {
                quarter = 4;
                quarterName = "春";
            } else if (List.of(6, 7, 8).contains(month)) {
                quarter = 7;
                quarterName = "夏";
            } else {
                quarter = 10;
                quarterName = "秋";
            }
            String quarterFormat = String.format("%02d", quarter);
            downloadPathTemplate = downloadPathTemplate.replace("${quarter}", String.valueOf(quarter));
            downloadPathTemplate = downloadPathTemplate.replace("${quarterFormat}", quarterFormat);
            downloadPathTemplate = downloadPathTemplate.replace("${quarterName}", quarterName);
        }

        downloadPathTemplate = downloadPathTemplate.replace("${tmdbYear}", String.valueOf(tmdbYear));
        downloadPathTemplate = downloadPathTemplate.replace("${year}", String.valueOf(year));
        downloadPathTemplate = downloadPathTemplate.replace("${month}", String.valueOf(month));
        downloadPathTemplate = downloadPathTemplate.replace("${monthFormat}", monthFormat);

        int season = ani.getSeason();
        String seasonFormat = String.format("%02d", season);

        downloadPathTemplate = downloadPathTemplate.replace("${season}", String.valueOf(season));
        downloadPathTemplate = downloadPathTemplate.replace("${seasonFormat}", seasonFormat);

        // 只有模板真正使用 ${bgmId} 时才解析，避免无 BGM 的合集因取不到 id 而失败。
        if (downloadPathTemplate.contains("${bgmId}")) {
            String bgmId = BgmUtil.getSubjectId(ani);
            downloadPathTemplate = downloadPathTemplate.replace("${bgmId}", bgmId);
        }

        List<Func1<Ani, Object>> list = List.of(
                Ani::getTitle,
                Ani::getThemoviedbName,
                Ani::getSubgroup
        );

        downloadPathTemplate = RenameUtil.replaceField(downloadPathTemplate, ani, list);

        String tmdbId = Opt.ofNullable(ani.getTmdb())
                .map(Tmdb::getId)
                .filter(StrUtil::isNotBlank)
                .orElse("");

        downloadPathTemplate = downloadPathTemplate.replace("${tmdbid}", tmdbId);

        if (downloadPathTemplate.contains("${jpTitle}")) {
            String jpTitle = RenameUtil.getJpTitle(ani);
            downloadPathTemplate = downloadPathTemplate.replace("${jpTitle}", jpTitle);
        }

        String downloadPath = FileUtils.getAbsolutePath(downloadPathTemplate);
        if (cacheKey != null) {
            cache.downloadPaths.put(cacheKey, downloadPath);
        }
        return downloadPath;
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

        if (downloadList) {
            List<TorrentsInfo> torrentsInfoList = TorrentUtil.findTorrentsInfosByAni(ani);
            for (TorrentsInfo torrentsInfo : torrentsInfoList) {
                String name = torrentsInfo.getName();
                if (!name.equalsIgnoreCase(reName)) {
                    continue;
                }
                log.info("已存在下载任务 {}", reName);
                TorrentUtil.saveTorrent(ani, item);
                return true;
            }
        }

        String downloadPath = getDownloadPath(ani);
        List<File> files = listFilesCached(downloadPath);

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
