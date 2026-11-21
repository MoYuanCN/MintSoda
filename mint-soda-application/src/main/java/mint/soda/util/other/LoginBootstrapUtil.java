package mint.soda.util.other;

import mint.soda.commons.CredentialUtils;
import mint.soda.entity.Config;
import mint.soda.entity.Login;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.OsInfo;
import cn.hutool.system.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.net.URI;
import java.util.UUID;

/**
 * 首次启动的登录凭据初始化。
 * <p>
 * Windows 首次启动不再内置弱口令，改为打开浏览器引导设置账号；
 * Linux 首次启动生成随机强密码并只输出一次；环境变量可强制覆盖两者。
 */
@Slf4j
public class LoginBootstrapUtil {
    public static final String ENV_USERNAME = "MINT_SODA_USERNAME";
    public static final String ENV_PASSWORD = "MINT_SODA_PASSWORD";

    /**
     * 是否等待用户在 WebUI 完成初始化。
     */
    private static volatile boolean setupRequired = false;

    /**
     * 配置是否已加载完成，避免启动早期用默认值误判初始化状态。
     */
    private static volatile boolean ready = false;

    public static boolean isSetupRequired() {
        return ready && setupRequired;
    }

    /**
     * 配置中是否已经存在可用的登录凭据。
     *
     * @param config 配置
     * @param login  登录凭据
     * @return 是否已配置
     */
    private static boolean hasCredentials(Config config, Login login) {
        if (Boolean.TRUE.equals(config.getLoginConfigured())) {
            return true;
        }
        return StrUtil.isNotBlank(login.getUsername()) && StrUtil.isNotBlank(login.getPassword());
    }

    /**
     * 是否通过 --setup 强制进入初始化引导。
     *
     * @return 是否强制引导
     */
    private static boolean forceSetup() {
        return "true".equalsIgnoreCase(SystemUtil.get("SETUP", "false"))
                || "true".equalsIgnoreCase(System.getProperty("SETUP", "false"));
    }

    /**
     * 启动时检查登录凭据状态。
     *
     * @param port 服务端口，用于打开浏览器
     */
    public static void init(String port) {
        Config config = ConfigUtil.CONFIG;
        Login login = config.getLogin();
        if (login == null) {
            login = new Login();
            config.setLogin(login);
        }

        if (applyEnvOverride(config, login)) {
            ready = true;
            return;
        }

        // 已有可用凭据时 --setup 完全失效，避免把已配置的账号重新变成待初始化。
        if (hasCredentials(config, login)) {
            if (forceSetup()) {
                log.warn("账号已配置，忽略 --setup；如需重设账号请在设置页修改或使用 {} / {} 覆盖",
                        ENV_USERNAME, ENV_PASSWORD);
            }
            setupRequired = false;
            ready = true;
            return;
        }

        // 仅在尚未配置凭据时，显式要求初始化才进入引导。
        if (forceSetup()) {
            setupRequired = true;
            ready = true;
            log.warn("已通过 --setup 进入初始化引导，请在 WebUI 设置登录用户名与密码");
            return;
        }

        OsInfo osInfo = SystemUtil.getOsInfo();
        if (osInfo.isWindows()) {
            setupRequired = true;
            ready = true;
            log.warn("检测到首次启动，请在浏览器中设置登录用户名与密码");
            openBrowser(port);
            return;
        }

        String username = CredentialUtils.randomUsername();
        String rawPassword = CredentialUtils.randomPassword();
        login.setUsername(username)
                .setPassword(CredentialUtils.md5(rawPassword));
        config.setLoginConfigured(true)
                .setTokenId(UUID.randomUUID().toString());
        ConfigUtil.sync();
        setupRequired = false;

        log.info("首次启动已生成登录凭据，请妥善保存");
        log.info("用户名: {}", username);
        log.info("密码: {}", rawPassword);
        log.info("后续启动不再输出，也可通过 {} / {} 环境变量覆盖", ENV_USERNAME, ENV_PASSWORD);
        ready = true;
    }

    /**
     * 处理环境变量强制覆盖。
     *
     * @return 是否已应用覆盖
     */
    private static boolean applyEnvOverride(Config config, Login login) {
        String username = StrUtil.trimToEmpty(SystemUtil.get(ENV_USERNAME, ""));
        String password = StrUtil.trimToEmpty(SystemUtil.get(ENV_PASSWORD, ""));
        if (StrUtil.isBlank(username) && StrUtil.isBlank(password)) {
            return false;
        }

        boolean changed = false;
        if (StrUtil.isNotBlank(username)) {
            String error = CredentialUtils.checkUsername(username);
            if (error != null) {
                throw new IllegalStateException(StrUtil.format("{} 不合法: {}", ENV_USERNAME, error));
            }
            if (!username.equals(login.getUsername())) {
                login.setUsername(username);
                changed = true;
            }
        }

        if (StrUtil.isNotBlank(password)) {
            String error = CredentialUtils.checkPassword(password);
            if (error != null) {
                throw new IllegalStateException(StrUtil.format("{} 过弱: {}", ENV_PASSWORD, error));
            }
            String passwordHash = CredentialUtils.md5(password);
            if (!passwordHash.equalsIgnoreCase(login.getPassword())) {
                login.setPassword(passwordHash);
                changed = true;
            }
        }

        if (!changed && Boolean.TRUE.equals(config.getLoginConfigured())) {
            // 凭据已与当前配置一致，重启不需要重写配置或让既有会话失效。
            setupRequired = false;
            return true;
        }

        config.setLoginConfigured(true)
                .setTokenId(UUID.randomUUID().toString());
        ConfigUtil.sync();
        setupRequired = false;
        log.info("已通过环境变量覆盖登录凭据");
        return true;
    }

    /**
     * 保存首次启动设置的用户名与密码。
     * <p>
     * 接口只允许本机或内网调用，这里接收明文是为了在服务端强制校验强度，
     * 落库时只保存摘要。
     *
     * @param username 用户名
     * @param password 明文密码
     */
    public static void completeSetup(String username, String password) {
        String usernameError = CredentialUtils.checkUsername(username);
        if (usernameError != null) {
            throw new IllegalArgumentException(usernameError);
        }
        String passwordError = CredentialUtils.checkPassword(password);
        if (passwordError != null) {
            throw new IllegalArgumentException(passwordError);
        }

        Config config = ConfigUtil.CONFIG;
        config.setLogin(new Login()
                        .setUsername(username)
                        .setPassword(CredentialUtils.md5(password)))
                .setLoginConfigured(true)
                .setTokenId(UUID.randomUUID().toString());
        ConfigUtil.sync();
        setupRequired = false;
    }

    private static void openBrowser(String port) {
        try {
            if (!Desktop.isDesktopSupported()) {
                log.warn("无法自动打开浏览器，请手动访问 http://127.0.0.1:{}", port);
                return;
            }
            Desktop.getDesktop().browse(new URI("http://127.0.0.1:" + port));
        } catch (Exception e) {
            log.warn("打开浏览器失败，请手动访问 http://127.0.0.1:{}", port);
        }
    }
}
