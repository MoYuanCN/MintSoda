package mint.soda.service;

import mint.soda.commons.FileUtils;
import mint.soda.commons.MavenUtils;
import mint.soda.download.qBittorrent;
import mint.soda.entity.Config;
import mint.soda.entity.GitInfo;
import mint.soda.entity.Login;
import mint.soda.entity.ProxyTest;
import mint.soda.entity.web.Result;
import mint.soda.entity.web.ResultCode;
import mint.soda.start.BaseStart;
import mint.soda.util.basic.HttpReq;
import mint.soda.util.other.ConfigUtil;
import mint.soda.util.other.TorrentUtil;
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
        } else {
            // 前端传的是明文摘要，这里无法还原明文，只拦下明显的弱口令摘要。
            if (mint.soda.commons.CredentialUtils.md5("admin").equalsIgnoreCase(loginPassword)) {
                throw new IllegalArgumentException("不能使用 admin 作为密码");
            }
            config.setLoginConfigured(true);
        }
        if (StrUtil.isBlank(loginUsername)) {
            config.getLogin().setUsername(username);
        } else if (!loginUsername.equals(username)) {
            // 仅校验新用户名，避免历史配置里的用户名让设置无法保存。
            String usernameError = mint.soda.commons.CredentialUtils.checkUsername(loginUsername);
            if (usernameError != null) {
                throw new IllegalArgumentException(usernameError);
            }
        }
        // 初始化状态由服务端维护；只有确实存在凭据时才标记完成，
        // 避免空凭据的配置通过保存接口绕过首次初始化。
        Login newLogin = config.getLogin();
        if (StrUtil.isNotBlank(newLogin.getUsername()) && StrUtil.isNotBlank(newLogin.getPassword())) {
            config.setLoginConfigured(true);
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
        Long size = clearService.clearUnusedCache();

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
