package ani.rss.service;

import ani.rss.commons.FileUtils;
import ani.rss.commons.MavenUtils;
import ani.rss.download.qBittorrent;
import ani.rss.entity.Config;
import ani.rss.entity.GitInfo;
import ani.rss.entity.Login;
import ani.rss.entity.ProxyTest;
import ani.rss.entity.web.Result;
import ani.rss.entity.web.ResultCode;
import ani.rss.start.BaseStart;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.ConfigUtil;
import ani.rss.util.other.TorrentUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.http.HttpRequest;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class ConfigService {

    @Resource
    private ClearService clearService;

    @Resource
    private TaskService taskService;

    @Resource
    private GitProperties gitProperties;

    public Config config() {
        String version = MavenUtils.getVersion();
        Config config = ObjectUtil.clone(ConfigUtil.CONFIG);
        config.getLogin().setPassword("");
        config.setDownloadToolPassword("").setQbPassword("");
        config.setVersion(version)
                .setGitInfo(getGitInfo()).setOutTradeNo("").setJwtKey("");
        return config;
    }

    public GitInfo getGitInfo() {
        return new GitInfo()
                .setBranch(gitProperties.getBranch())
                .setShortCommitId(gitProperties.getShortCommitId())
                .setCommitId(gitProperties.getCommitId());
    }

    public void setConfig(Config newConfig) {
        Config config = ConfigUtil.CONFIG;
        if (newConfig.getCustomTorrentUrl() != null) {
            String customTorrentUrl = StrUtil.trimToEmpty(newConfig.getCustomTorrentUrl());
            if (StrUtil.isNotBlank(customTorrentUrl)
                    && !(customTorrentUrl.startsWith("http://") || customTorrentUrl.startsWith("https://"))) {
                throw new IllegalArgumentException("自定义种子地址必须以 http:// 或 https:// 开头");
            }
            newConfig.setCustomTorrentUrl(customTorrentUrl);
        }
        Login login = config.getLogin();
        String username = login.getUsername();
        String password = login.getPassword();
        Integer renameSleepSeconds = config.getRenameSleepSeconds();
        Integer sleep = config.getRssSleepSeconds();
        Boolean autoStart = config.getAutoStart();

        if (StrUtil.isBlank(newConfig.getDownloadToolPassword())) {
            newConfig.setDownloadToolPassword(config.getDownloadToolPassword());
        }
        if (StrUtil.isBlank(newConfig.getQbPassword())) {
            newConfig.setQbPassword(config.getQbPassword());
        }

        newConfig.setOutTradeNo(null).setJwtKey(null);
        if (newConfig.getRssSleepSeconds() == null && newConfig.getRssSleepMinutes() != null) {
            newConfig.setRssSleepSeconds(newConfig.getRssSleepMinutes() * 60);
        }

        CopyOptions copyOptions = CopyOptions
                .create()
                .setIgnoreNullValue(true);

        BeanUtil.copyProperties(
                newConfig,
                config,
                copyOptions
        );

        String loginUsername = config.getLogin().getUsername();
        String loginPassword = config.getLogin().getPassword();

        // 密码或用户名发生改动
        if (!loginUsername.equals(username) || StrUtil.isNotBlank(loginPassword)) {
            config.setTokenId(UUID.randomUUID().toString());
        }

        // 密码未发生修改
        if (StrUtil.isBlank(loginPassword)) {
            config.getLogin().setPassword(password);
        }
        if (StrUtil.isBlank(loginUsername)) {
            config.getLogin().setUsername(username);
        }

        Boolean proxy = config.getProxy();
        if (proxy) {
            String proxyHost = config.getProxyHost();
            Integer proxyPort = config.getProxyPort();
            if (StrUtil.isBlank(proxyHost) || Objects.isNull(proxyPort)) {
                throw new IllegalArgumentException("代理参数不完整");
            }
        }

        ConfigUtil.sync();
        Integer newRenameSleepSeconds = config.getRenameSleepSeconds();
        Integer newSleep = config.getRssSleepSeconds();
        Boolean newAutoStart = config.getAutoStart();

        // 时间间隔发生改变，重启任务
        if (
                !Objects.equals(newSleep, sleep) ||
                        !Objects.equals(newRenameSleepSeconds, renameSleepSeconds)
        ) {
            taskService.restart();
        }
        // 开机自启发生改变
        if (!newAutoStart.equals(autoStart)) {
            if (BaseStart.isSupported()) {
                BaseStart instance = BaseStart.getInstance();
                instance.sync();
            }
        }
    }

    public String clearCache() {
        File configDir = ConfigUtil.getConfigDir();
        String configDirStr = FileUtils.getAbsolutePath(configDir);

        Long size = clearService.clearCover();

        // 清理 mikan 预览封面
        FileUtil.del(configDirStr + "/img");

        return FileUtils.formatSize(size, true);
    }

    public ProxyTest testProxy(String url, Config config) {
        url = Base64.decodeStr(url);

        log.info(url);

        HttpRequest httpRequest = HttpReq.get(url);
        HttpReq.setProxy(httpRequest, config);

        ProxyTest proxyTest = new ProxyTest();
        Result<ProxyTest> result = Result.success(proxyTest);

        long start = LocalDateTimeUtil.toEpochMilli(LocalDateTimeUtil.now());
        try {
            httpRequest
                    .then(res -> {
                        int status = res.getStatus();
                        proxyTest.setStatus(status);

                        String title = Jsoup.parse(res.body())
                                .title();
                        proxyTest.setTitle(title);
                    });
        } catch (Exception e) {
            result.setMessage(e.getMessage())
                    .setCode(ResultCode.HTTP_INTERNAL_ERROR);
        }

        long end = LocalDateTimeUtil.toEpochMilli(LocalDateTimeUtil.now());
        proxyTest.setTime(end - start);
        return proxyTest;
    }

    public Boolean downloadLoginTest(Config config) {
        ConfigUtil.format(config);
        config.setDownloadToolType("qBittorrent");
        qBittorrent download = SpringUtil.getBean(qBittorrent.class);
        return download.login(true, config);
    }


}
