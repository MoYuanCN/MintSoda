package mint.soda.controller;

import mint.soda.auth.AuthUtil;
import mint.soda.cache.CacheUtils;
import mint.soda.entity.Config;
import mint.soda.entity.Login;
import mint.soda.entity.web.Result;
import mint.soda.entity.web.ResultCode;
import mint.soda.util.other.ConfigUtil;
import mint.soda.util.other.LoginBootstrapUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
public class LoginController extends BaseController {

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<String> login(@RequestBody Login myLogin) {
        if (LoginBootstrapUtil.isSetupRequired()) {
            throw new IllegalStateException("请先完成首次启动的账号设置");
        }
        AuthUtil.limitLoginAttempts(false);

        Config config = ConfigUtil.CONFIG;
        Login login = config.getLogin();

        String myUsername = myLogin.getUsername();
        String myPassword = myLogin.getPassword();

        Assert.notBlank(myUsername, "用户名不能为空");
        Assert.notBlank(myPassword, "密码不能为空");

        String username = login.getUsername();
        String password = login.getPassword();

        String ip = AuthUtil.getIp();

        if (username.equals(myUsername) && password.equals(myPassword)) {
            AuthUtil.resetSessionId();
            clearLimitLoginAttempts();
            log.info("登录成功 {} ip: {}", username, ip);
            String s = AuthUtil.getToken();
            return new Result<String>()
                    .setCode(ResultCode.HTTP_OK)
                    .setMessage("登录成功")
                    .setData(s);
        }
        AuthUtil.limitLoginAttempts(true);
        log.warn("登陆失败 {} ip: {}", myUsername, ip);
        ThreadUtil.sleep(RandomUtil.randomInt(500, 5000));
        return Result.error("用户名或密码错误");
    }

    /**
     * 首次启动初始化状态，登录页在渲染前调用。
     */
    @Operation(summary = "获取首次启动初始化状态")
    @PostMapping("/setupStatus")
    public Result<Map<String, Object>> setupStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("required", LoginBootstrapUtil.isSetupRequired());
        map.put("passwordMinLength", mint.soda.commons.CredentialUtils.MIN_PASSWORD_LENGTH);
        return Result.success(map);
    }

    /**
     * 首次启动设置用户名与密码摘要。
     * <p>
     * 仅在尚未完成初始化时可用，只允许本机或内网地址调用，避免公网抢先占用实例。
     */
    @Operation(summary = "首次启动设置账号")
    @PostMapping("/setup")
    public Result<Void> setup(@RequestBody mint.soda.entity.dto.SetupDTO setupDTO) {
        if (!LoginBootstrapUtil.isSetupRequired()) {
            throw new IllegalStateException("已完成初始化，请直接登录");
        }
        String ip = AuthUtil.getIp();
        if (!NetUtil.isInnerIP(ip) && !"127.0.0.1".equals(ip) && !"::1".equals(ip)) {
            throw new IllegalStateException("仅允许本机或内网地址完成初始化");
        }
        LoginBootstrapUtil.completeSetup(setupDTO.getUsername(), setupDTO.getPassword());
        log.info("首次启动已设置登录凭据 ip: {}", ip);
        return Result.success("初始化完成，请使用新账号登录");
    }

    /**
     * 清除限制尝试次数
     */
    private void clearLimitLoginAttempts() {
        String ip = AuthUtil.getIp();
        String key = "LimitLoginAttempts#" + ip;
        if (CacheUtils.containsKey(key)) {
            CacheUtils.remove(key);
        }
    }
}
