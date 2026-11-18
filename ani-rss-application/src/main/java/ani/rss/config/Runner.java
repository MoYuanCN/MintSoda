package ani.rss.config;

import ani.rss.commons.ExceptionUtils;
import ani.rss.commons.MavenUtils;
import ani.rss.service.BackupService;
import ani.rss.service.TaskService;
import ani.rss.util.other.AniUtil;
import ani.rss.util.other.ConfigUtil;
import ani.rss.util.other.LoginBootstrapUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.RuntimeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Slf4j
@Component
public class Runner implements ApplicationRunner {

    @Resource
    private BackupService backupService;

    @Resource
    private TaskService taskService;

    @Resource
    private ani.rss.plugin.PluginManager pluginManager;

    @Value("${server.port}")
    private String port;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        try {
            ConfigUtil.load();
            LoginBootstrapUtil.init(port);
            backupService.backup();

            AniUtil.load();
            pluginManager.init();
            taskService.start();
            String version = MavenUtils.getVersion();
            log.info("version {}", version);


            for (String ip : NetUtil.localIpv4s()) {
                InetSocketAddress inetSocketAddress = new InetSocketAddress(ip, Integer.parseInt(port));
                if (NetUtil.isOpen(inetSocketAddress, 100)) {
                    log.info("http://{}:{}", ip, port);
                }
            }
        } catch (Exception e) {
            String message = ExceptionUtils.getMessage(e);
            log.error(message, e);
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
                // 环境变量里的用户名或强口令不合规时直接给结论，避免只看到堆栈。
                log.error("启动失败: {}", cause.getMessage());
            }
            System.exit(1);
        }
        RuntimeUtil.addShutdownHook(() -> log.info("程序退出..."));
    }
}
