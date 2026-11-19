package ani.rss.download;

import ani.rss.commons.ExceptionUtils;
import ani.rss.commons.FileUtils;
import ani.rss.commons.GsonStatic;
import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.entity.Item;
import ani.rss.entity.torrent.TorrentsInfo;
import ani.rss.entity.torrent.qBittorrentTorrentsInfo;
import ani.rss.enums.StringEnum;
import ani.rss.enums.TorrentsTagEnum;
import ani.rss.service.DownloadService;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.ConfigUtil;
import ani.rss.util.other.TorrentUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * qBittorrent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class qBittorrent implements BaseDownload {

    private static final Config CONFIG = ConfigUtil.CONFIG;
    private static volatile String sessionCookie = "";

    private final DownloadService downloadService;


    /**
     * 获取对应任务的文件列表
     *
     * @param torrentsInfo 种子信息
     * @param filter       过滤出视频与字幕
     * @return 文件列表
     */
    public static List<qBittorrentTorrentsInfo.FileEntity> files(TorrentsInfo torrentsInfo, Boolean filter) {
        String hash = torrentsInfo.getHash();

        return getApi("/api/v2/torrents/files")
                .form("hash", hash)
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    List<qBittorrentTorrentsInfo.FileEntity> list = GsonStatic.fromJsonList(res.body(), qBittorrentTorrentsInfo.FileEntity.class);
                    return list.stream()
                            .filter(fileEntity -> {
                                if (!filter) {
                                    return true;
                                }
                                String name = fileEntity.getName();
                                String extName = FileUtil.extName(name);
                                if (StrUtil.isBlank(extName)) {
                                    return false;
                                }
                                extName = extName.toLowerCase();
                                Long size = fileEntity.getSize();
                                if (size < 1) {
                                    return false;
                                }
                                return FileUtils.isVideoFormat(extName) || FileUtils.isSubtitleFormat(extName);
                            })
                            .sorted((fileEntity1, fileEntity2) -> Long.compare(fileEntity2.getSize(), fileEntity1.getSize()))
                            .toList();
                });
    }

    @Override
    public Boolean login(Boolean test, Config config) {
        String host = config.getDownloadToolHost();
        String authType = StrUtil.blankToDefault(config.getQbAuthType(), "API_KEY");

        if (StrUtil.isBlank(host)) {
            log.warn("qBittorrent 未配置完成");
            return false;
        }

        try {
            if ("USER_PASSWORD".equalsIgnoreCase(authType)) {
                String username = config.getQbUsername();
                String password = config.getQbPassword();
                if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
                    log.warn("qBittorrent 用户名或密码未配置");
                    return false;
                }
                return HttpReq.post(host + "/api/v2/auth/login")
                        .form("username", username)
                        .form("password", password)
                        .thenFunction(response -> {
                            if (!response.isOk() || !"Ok.".equalsIgnoreCase(response.body().trim())) {
                                return false;
                            }
                            String cookie = response.header(Header.SET_COOKIE);
                            if (StrUtil.isBlank(cookie)) {
                                return false;
                            }
                            sessionCookie = cookie.split(";", 2)[0];
                            return true;
                        });
            }

            String apiKey = config.getDownloadToolPassword();
            if (StrUtil.isBlank(apiKey) || !apiKey.startsWith("qbt_")) {
                log.warn("qBittorrent ApiKey 未正确配置");
                return false;
            }
            sessionCookie = "";
            return HttpReq.post(host + "/api/v2/app/version")
                    .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                    .thenFunction(HttpResponse::isOk);
        } catch (Exception e) {
            String message = ExceptionUtils.getMessage(e);
            log.error("登录 qBittorrent 失败 {}", message);
        }
        return false;
    }

    @Override
    public Boolean download(Ani ani, Item item, String savePath, File torrentFile) {
        String name = item.getReName();
        Boolean qbUseDownloadPath = CONFIG.getQbUseDownloadPath();

        List<String> tags = newTags(ani, item);

        Integer ratioLimit = CONFIG.getRatioLimit();
        Integer seedingTimeLimit = CONFIG.getSeedingTimeLimit();
        Integer inactiveSeedingTimeLimit = CONFIG.getInactiveSeedingTimeLimit();
        Boolean rename = CONFIG.getRename();

        Long upLimit = CONFIG.getUpLimit() * 1024;
        Long dlLimit = CONFIG.getDlLimit() * 1024;

        String qbContentLayout = CONFIG.getQbContentLayout();

        HttpRequest httpRequest = postApi("/api/v2/torrents/add")
                .form("addToTopOfQueue", false)
                .form("autoTMM", false)
                .form("category", TorrentsTagEnum.ANI_RSS.getValue())
                .form("contentLayout", qbContentLayout)
                .form("dlLimit", dlLimit)
                .form("firstLastPiecePrio", false)
                .form("rename", name)
                .form("savepath", savePath)
                .form("sequentialDownload", false)
                .form("skip_checking", false)
                .form("stopCondition", "None")
                .form("upLimit", upLimit)
                .form("useDownloadPath", qbUseDownloadPath)
                .form("tags", CollUtil.join(tags, ","))
                .form("ratioLimit", ratioLimit)
                .form("seedingTimeLimit", seedingTimeLimit)
                .form("inactiveSeedingTimeLimit", inactiveSeedingTimeLimit);

        String extName = FileUtil.extName(torrentFile);
        if ("txt".equals(extName)) {
            httpRequest
                    .form("paused", false)
                    .form("stopped", false)
                    .form("urls", FileUtil.readUtf8String(torrentFile));
        } else {
            if (torrentFile.length() > 0) {
                // 开启了重命名则在重命名后再开始下载
                httpRequest.form("paused", rename)
                        .form("stopped", rename)
                        .form("torrents", torrentFile);
            } else {
                httpRequest
                        .form("paused", false)
                        .form("stopped", false)
                        .form("urls", "magnet:?xt=urn:btih:" + FileUtil.mainName(torrentFile));
            }
        }
        httpRequest.thenFunction(HttpResponse::isOk);

        String hash = FileUtil.mainName(torrentFile);

        // 轮询确认任务已出现在下载器中：命中即返回，避免固定等待。
        for (int i = 0; i < 10; i++) {
            List<TorrentsInfo> torrentsInfos = TorrentUtil.refreshTorrentsInfos();
            Optional<TorrentsInfo> optionalTorrentsInfo = torrentsInfos
                    .stream()
                    .filter(torrentsInfo ->
                            torrentsInfo.getHash().equals(hash) ||
                                    torrentsInfo.getName().equals(name)
                    )
                    .findFirst();
            if (optionalTorrentsInfo.isPresent()) {
                return true;
            }
            ThreadUtil.sleep(i < 2 ? 500 : 1000);
        }
        return false;
    }

    /**
     * 开始下载
     *
     * @param torrentsInfo 种子信息
     * @return 下载是否成功
     */
    public static Boolean start(TorrentsInfo torrentsInfo) {
        return postApi("/api/v2/torrents/start")
                .form("hashes", torrentsInfo.getHash())
                .thenFunction(HttpResponse::isOk);
    }

    @Override
    public List<TorrentsInfo> getTorrentsInfos() {
        String host = CONFIG.getDownloadToolHost();
        if (StrUtil.isBlank(host) || !configured()) {
            return new ArrayList<>();
        }

        try {
            return getApi("/api/v2/torrents/info")
                    .thenFunction(res -> {
                        List<qBittorrentTorrentsInfo> torrentsInfos = GsonStatic.fromJsonList(res.body(), qBittorrentTorrentsInfo.class);
                        return torrentsInfos.stream()
                                .map(qBittorrentTorrentsInfo::toTorrentsInfo)
                                .filter(torrentsInfo -> {
                                    // 过滤出本应用的标签或分类，兼容旧标识
                                    if (TorrentsTagEnum.matches(torrentsInfo.getCategory())) {
                                        return true;
                                    }

                                    List<String> tagList = torrentsInfo.getTagList();
                                    return tagList.stream().anyMatch(TorrentsTagEnum::matches);
                                })
                                .toList();
                    });
        } catch (Exception ignored) {
        }
        return new ArrayList<>();
    }

    /**
     * 当前认证方式所需凭据是否已配置。
     * API Key 模式需要以 qbt_ 开头的密钥；用户名密码模式需要账号和密码。
     *
     * @return 是否已配置
     */
    private static boolean configured() {
        if ("USER_PASSWORD".equalsIgnoreCase(CONFIG.getQbAuthType())) {
            return StrUtil.isNotBlank(CONFIG.getQbUsername())
                    && StrUtil.isNotBlank(CONFIG.getQbPassword());
        }
        return StrUtil.isNotBlank(CONFIG.getDownloadToolPassword());
    }

    @Override
    public Boolean delete(TorrentsInfo torrentsInfo, Boolean deleteFiles) {
        String name = torrentsInfo.getName();
        String hash = torrentsInfo.getHash();
        try {
            List<qBittorrentTorrentsInfo.FileEntity> files = files(torrentsInfo, false);
            boolean b = postApi("/api/v2/torrents/delete")
                    .form("hashes", hash)
                    .form("deleteFiles", deleteFiles)
                    .thenFunction(HttpResponse::isOk);
            if (!b) {
                return false;
            }

            // 剧场版不用进行残留的文件夹清理
            if (!ReUtil.contains(StringEnum.SEASON_REG, name)) {
                return true;
            }

            String downloadDir = torrentsInfo.getSavePath();

            List<File> dirList = files.stream()
                    .map(qBittorrentTorrentsInfo.FileEntity::getName)
                    .map(File::new)
                    .map(File::getParent)
                    .filter(StrUtil::isNotBlank)
                    .map(s -> downloadDir + "/" + s)
                    .distinct()
                    .map(File::new)
                    .filter(File::exists)
                    .filter(File::isDirectory)
                    .toList();

            Boolean subtitleIndependentFolderEnabled = CONFIG.getSubtitleIndependentFolderEnabled();
            String subtitleIndependentFolderName = CONFIG.getSubtitleIndependentFolderName();

            // 清空剩余文件夹
            for (File file : dirList) {
                if (subtitleIndependentFolderEnabled) {
                    if (subtitleIndependentFolderName.equals(file.getName())) {
                        // 字幕独立文件夹 不进行删除
                        continue;
                    }
                }

                log.info("删除剩余文件夹: {}", file);
                try {
                    FileUtil.del(file);
                } catch (Exception e) {
                    log.info("删除失败: {}", file);
                    log.error(e.getMessage(), e);
                }
            }

            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Boolean rename(TorrentsInfo torrentsInfo) {
        String reName = torrentsInfo.getName();

        if (StrUtil.isBlank(reName) || !ReUtil.contains(StringEnum.SEASON_REG, reName)) {
            // 剧场版 OR OVA 直接开始任务
            Boolean start = start(torrentsInfo);
            Assert.isTrue(start, "开始任务失败 {}", reName);
            if (start) {
                log.info("开始任务 {}", reName);
            }
            return true;
        }

        String hash = torrentsInfo.getHash();

        Optional<Ani> aniOpt = downloadService.findAniByDownloadPath(torrentsInfo);

        if (aniOpt.isEmpty()) {
            log.error("未能获取番剧对象: {}", torrentsInfo.getName());
            return false;
        }

        Ani ani = aniOpt.get();

        List<String> priorityKeywords = getPriorityKeywords(ani);

        List<qBittorrentTorrentsInfo.FileEntity> files = files(torrentsInfo, true);

        if (!priorityKeywords.isEmpty()) {
            files = files.stream()
                    .sorted(Comparator.comparingInt(file -> {
                        String fileName = file.getName();
                        String mainName = FileUtil.mainName(fileName);
                        int minIndex = Integer.MAX_VALUE;
                        for (int i = 0; i < priorityKeywords.size(); i++) {
                            String priorityKeyword = priorityKeywords.get(i);
                            if (!mainName.contains(priorityKeyword)) {
                                continue;
                            }
                            minIndex = Math.min(minIndex, i);
                        }
                        return minIndex;
                    }))
                    .toList();
        }

        List<String> names = files.stream()
                .map(qBittorrentTorrentsInfo.FileEntity::getName)
                .toList();

        if (files.isEmpty()) {
            log.debug("{} 磁力链接还在获取原数据中", hash);
            return false;
        }

        Boolean subtitleIndependentFolderEnabled = CONFIG.getSubtitleIndependentFolderEnabled();
        String subtitleIndependentFolderName = CONFIG.getSubtitleIndependentFolderName();

        List<String> newNames = new ArrayList<>();

        for (qBittorrentTorrentsInfo.FileEntity fileEntity : files) {
            String name = fileEntity.getName();
            String newPath = getFileReName(name, reName);

            if (
                    FileUtils.isSubtitleFormat(newPath) &&
                            subtitleIndependentFolderEnabled &&
                            StrUtil.isNotBlank(subtitleIndependentFolderName)
            ) {
                // 字幕独立文件夹
                newPath = subtitleIndependentFolderName + "/" + newPath;
            }

            if (names.contains(newPath)) {
                continue;
            }
            if (newNames.contains(newPath)) {
                // 停止不必要的文件下载
                postApi("/api/v2/torrents/filePrio")
                        .form("hash", hash)
                        .form("id", fileEntity.getIndex())
                        .form("priority", 0)
                        .thenFunction(HttpResponse::isOk);
                continue;
            }
            newNames.add(newPath);

            // 文件名未发生改变
            if (name.equals(newPath)) {
                continue;
            }

            log.info("重命名 {} ==> {}", name, newPath);

            Boolean b = postApi("/api/v2/torrents/renameFile")
                    .form("hash", hash)
                    .form("oldPath", name)
                    .form("newPath", newPath)
                    .thenFunction(HttpResponse::isOk);
            Assert.isTrue(b, "重命名失败 {} ==> {}", name, newPath);
        }

        Boolean start = start(torrentsInfo);
        Assert.isTrue(start, "开始任务失败 {}", reName);
        log.info("开始任务 {}", reName);

        if (newNames.isEmpty()) {
            return true;
        }

        // qb重命名具有延迟，等待重命名完成
        for (int i = 0; i < 10; i++) {
            ThreadUtil.sleep(1000);
            names = torrentsInfo.getFilesSupplier().get();
            if (new HashSet<>(names).containsAll(newNames)) {
                return true;
            }
        }

        log.warn("重命名貌似出现了问题？{}", reName);
        return false;
    }

    @Override
    public Boolean addTags(TorrentsInfo torrentsInfo, String tags) {
        String hash = torrentsInfo.getHash();
        return postApi("/api/v2/torrents/addTags")
                .form("hashes", hash)
                .form("tags", tags)
                .thenFunction(res -> {
                    boolean ok = res.isOk();
                    if (!ok) {
                        log.error(res.body());
                    }
                    return ok;
                });
    }

    @Override
    public void setSavePath(TorrentsInfo torrentsInfo, String path) {
        postApi("/api/v2/torrents/setAutoManagement")
                .form("hashes", torrentsInfo.getHash())
                .form("enable", false)
                .thenFunction(HttpResponse::isOk);
        postApi("/api/v2/torrents/setSavePath")
                .form("id", torrentsInfo.getHash())
                .form("path", path)
                .then(req -> {
                    if (!req.isOk()) {
                        log.error(req.body());
                    }
                });
    }

    private static List<String> getPriorityKeywords(Ani ani) {
        Boolean priorityKeywordsEnable = CONFIG.getPriorityKeywordsEnable();
        Boolean customPriorityKeywordsEnable = ani.getCustomPriorityKeywordsEnable();

        if (customPriorityKeywordsEnable) {
            return ani.getCustomPriorityKeywords();
        }

        if (priorityKeywordsEnable) {
            return CONFIG.getPriorityKeywords();
        }

        return new ArrayList<>();
    }

    public static HttpRequest postApi(String path) {
        String downloadToolHost = CONFIG.getDownloadToolHost();
        HttpRequest request = HttpReq.post(downloadToolHost + path);
        return applyAuthentication(request);
    }

    public static HttpRequest getApi(String path) {
        String downloadToolHost = CONFIG.getDownloadToolHost();
        HttpRequest request = HttpReq.get(downloadToolHost + path);
        return applyAuthentication(request);
    }

    private static HttpRequest applyAuthentication(HttpRequest request) {
        if ("USER_PASSWORD".equalsIgnoreCase(CONFIG.getQbAuthType())) {
            if (StrUtil.isNotBlank(sessionCookie)) {
                request.header(Header.COOKIE, sessionCookie);
            }
            return request;
        }
        String apiKey = CONFIG.getDownloadToolPassword();
        return request.header(Header.AUTHORIZATION, "Bearer " + apiKey);
    }

}
