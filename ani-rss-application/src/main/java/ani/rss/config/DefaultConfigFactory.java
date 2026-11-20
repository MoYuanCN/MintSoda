package ani.rss.config;

import ani.rss.commons.FileUtils;
import ani.rss.entity.Config;
import ani.rss.entity.Login;
import ani.rss.entity.NotificationTemplate;
import ani.rss.enums.AniSortTypeEnum;
import ani.rss.enums.BgmTokenTypeEnum;
import ani.rss.enums.OffsetTypeEnum;
import ani.rss.util.other.RenameUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.system.OsInfo;
import cn.hutool.system.SystemUtil;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class DefaultConfigFactory {
    public static Config create() {
        Config config = new Config();
        String rootPath = "/Media";

        OsInfo osInfo = SystemUtil.getOsInfo();
        if (osInfo.isWindows()) {
            rootPath = FileUtil.getUserHomePath() + "/Downloads";
        }

        String downloadPath = FileUtils.getAbsolutePath(new File(rootPath, "番剧"));
        String ovaDownloadPath = FileUtils.getAbsolutePath(new File(rootPath, "剧场版"));
        String completedPath = FileUtils.getAbsolutePath(new File(rootPath, "已完结番剧"));

        String downloadPathTemplate = StrFormatter.format("{}/${title}/Season ${season}", downloadPath);
        String ovaDownloadPathTemplate = StrFormatter.format("{}/${title}", ovaDownloadPath);
        String completedPathTemplate = StrFormatter.format("{}/${title}/Season ${season}", completedPath);

        // 首次启动不再内置弱口令，凭据在 LoginBootstrapUtil 中按平台生成或引导设置。
        String password = "";

        String apiKey = RandomUtil.randomString(64).toLowerCase();

        String downloadToolHost = SystemUtil.get("DOWNLOAD_TOOL_HOST", "");
        String downloadToolPassword = SystemUtil.get("DOWNLOAD_TOOL_PASSWORD", "");

        String proxyList = """
                mikanani.me
                mikanime.tv
                anibt.net
                nyaa.si
                acg.rip
                google.com
                tmdb.org
                themoviedb.org
                anilist.co
                wushuo.top
                bgm.tv
                bangumi.tv
                chii.in
                github.com
                raw.githubusercontent.com
                telegram.org
                """;
        return config.setRssSleepSeconds(15 * 60)
                .setMikanHost("https://mikanani.me")
                .setTmdbApi("https://api.themoviedb.org")
                .setTmdbApiKey("")
                .setTmdbImage("https://image.tmdb.org")
                .setTmdbAnime(true)
                .setRenameSleepSeconds(10)
                .setRename(true)
                .setRss(true)
                .setRssTimeout(20)
                .setRssConcurrency(4)
                .setCustomTags(new ArrayList<>())
                .setDelayedDownload(0)
                .setFileExist(false)
                .setAwaitStalledUP(true)
                .setDelete(false)
                .setDeleteStandbyRSSOnly(false)
                .setOffset(false)
                .setOffsetType(OffsetTypeEnum.AUTO_OFFSET)
                .setTitleYear(true)
                .setAutoDisabled(false)
                .setDownloadPathTemplate(downloadPathTemplate)
                .setOvaDownloadPathTemplate(ovaDownloadPathTemplate)
                .setCollectionDownloadPathTemplate("")
                .setDownloadToolHost(downloadToolHost)
                .setDownloadToolType("qBittorrent")
                .setDownloadRetry(3)
                .setDownloadToolPassword(downloadToolPassword)
                .setQbAuthType("API_KEY")
                .setQbUsername("")
                .setQbPassword("")
                .setQbUseDownloadPath(false)
                .setQbContentLayout("Original")
                .setRatioLimit(-2)
                .setSeedingTimeLimit(-2)
                .setInactiveSeedingTimeLimit(-2)
                .setSkip5(true)
                .setStandbyRss(false)
                .setCoexist(false)
                .setLogsMax(128)
                .setDebug(false)
                .setCustomUserAgent("")
                .setDevHttpTrace(false)
                .setProxy(false)
                .setProxyHost("")
                .setProxyPort(8080)
                .setProxyUsername("")
                .setProxyPassword("")
                .setDownloadCount(0)
                // loginConfigured 保持 null，交给 ConfigUtil.format 依据已有凭据判断，
                // 否则升级旧配置时会被默认值覆盖成“未初始化”并重置账号。
                .setLogin(new Login()
                        .setUsername("")
                        .setPassword(password)
                )
                .setMultiLoginForbidden(true)
                .setLoginEffectiveHours(3)
                .setExclude(List.of("720[Pp]", "\\d-\\d", "合集", "特别篇"))
                .setGlobalMatch(new ArrayList<>())
                .setGlobalReverseMatch(new ArrayList<>())
                .setGlobalReverseMatchRules(new ArrayList<>())
                .setImportExclude(false)
                .setEnabledExclude(false)
                .setImportGlobalMatch(false)
                .setEnabledGlobalMatch(false)
                .setImportGlobalReverseMatch(false)
                .setEnabledGlobalReverseMatch(false)
                .setReverseMatchMissBehavior("SKIP")
                .setTmdb(true)
                .setBgmJpName(false)
                .setTmdbId(false)
                .setTmdbLanguage("zh-CN")
                .setTmdbRomaji(false)
                .setTmdbOriginalName(false)
                .setIpWhitelist(false)
                .setIpWhitelistStr("")
                .setOmit(true)
                .setBgmToken("")
                .setBgmTokenType(BgmTokenTypeEnum.INPUT)
                .setBgmAppID("")
                .setBgmAppSecret("")
                .setBgmRefreshToken("")
                .setBgmRedirectUri("")
                .setApiKey("")
                .setDownloadNew(false)
                .setInnerIP(false)
                .setRenameTemplate("[${subgroup}] ${title} S${seasonFormat}E${episodeFormat}")
                .setRenameDelYear(true)
                .setRenameDelTmdbId(true)
                .setPriorityKeywordsEnable(false)
                .setPriorityKeywords(new ArrayList<>())
                .setVerifyLoginIp(false)
                .setAutoUpdate(false)
                .setVersion("")
                .setBgmImageSize("medium")
                .setCustomCss("")
                .setCustomJs("")
                .setAppBackgroundImage("")
                .setLoginBackgroundImage("")
                .setAppBackgroundImageDark("")
                .setLoginBackgroundImageDark("")
                .setBackgroundBlur(10)
                .setBackgroundOpacity(100)
                .setLoginLogoImage("")
                .setAppLogoImage("")
                .setCardOpacity(80)
                .setCardBlur(14)
                .setAppCornerRadius(12)
                .setCustomTorrentUrl("")
                .setCustomEpisode(false)
                .setCustomEpisodeStr(RenameUtil.REG_STR)
                .setCustomEpisodeGroupIndex(2)
                .setUpLimit(0L)
                .setDlLimit(0L)
                .setGithubToken("")
                .setUpdateTotalEpisodeNumber(false)
                .setForceUpdateTotalEpisodeNumber(false)
                .setConfigBackup(false)
                .setConfigBackupDay(7)
                .setAccentColor("")
                .setThemeDynamicColor(false)
                .setNavBlur(18)
                .setNavOpacity(78)
                .setDialogBlur(16)
                .setDialogOpacity(84)
                .setCacheAutoClear(false)
                .setCacheAutoClearHours(72)
                .setCacheLastClearTime(0L)
                .setCompleted(false)
                .setCompletedPathTemplate(completedPathTemplate)
                .setNotificationTemplate("")
                .setNotificationConfigList(new ArrayList<>())
                .setDeveloperOptionsEnabled(false)
                .setPluginMarketUrl("")
                .setNotificationTemplateList(new ArrayList<>(List.of(
                        new NotificationTemplate()
                                .setId("default")
                                .setName("默认文本")
                                .setType("TEXT")
                                .setContent(NotificationTemplate.DEFAULT_TEXT_CONTENT)
                                .setTelegramTemplate(NotificationTemplate.DEFAULT_TEXT_CONTENT),
                        new NotificationTemplate()
                                .setId("default-json")
                                .setName("默认 JSON")
                                .setType("JSON")
                                .setContent(NotificationTemplate.DEFAULT_JSON_CONTENT)
                                .setCustomRequestBody(NotificationTemplate.DEFAULT_JSON_CONTENT))))
                .setApiKey(apiKey)
                .setSortType(AniSortTypeEnum.SCORE)
                .setTmdbIdPlexMode(false)
                .setProxyList(proxyList)
                .setFollowDay(14)
                .setBangumiIniEnabled(false)
                .setMaxFileNameLength(0)
                .setLimitLoginAttempts(true)
                .setReverseProxyTrustIpList(List.of("127.0.0.1"))
                .setReverseProxyTrustIpListEnabled(false)
                .setSubtitleIndependentFolderEnabled(false)
                .setSubtitleIndependentFolderName("Subs")
                .setBgmApi("https://api.bgm.tv")
                .setAutoStart(false)
                .setAllowCors(false)
                .setUuid(UUID.randomUUID().toString())
                .setJwtKey(createJwtKey())
                .setTokenId(UUID.randomUUID().toString());
    }

    private static String createJwtKey() {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[32]; // 32字节 = 256 bits
        random.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
