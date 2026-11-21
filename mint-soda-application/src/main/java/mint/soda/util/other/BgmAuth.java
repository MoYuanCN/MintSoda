package mint.soda.util.other;

import mint.soda.commons.GsonStatic;
import mint.soda.entity.BgmMe;
import mint.soda.entity.Config;
import mint.soda.enums.BgmTokenTypeEnum;
import mint.soda.util.basic.HttpReq;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Bangumi 账号与 Token 管理。
 * <p>
 * 从 {@link BgmUtil} 拆出的认证部分：注入认证头、查询有效期、自动刷新与读取当前用户。
 * 只处理“凭据生命周期”，与作品、剧集、季度的查询逻辑分开，便于独立测试与调整刷新策略。
 * <p>
 * 日志中不得输出 token 或 refresh token，只记录刷新结果与剩余天数。
 */
@Slf4j
public final class BgmAuth {

    private static final Config CONFIG = ConfigUtil.CONFIG;

    private BgmAuth() {
    }

    /**
     * 为请求注入 Bearer Token。
     *
     * @param httpRequest 请求
     * @return 同一请求
     */
    public static HttpRequest setToken(HttpRequest httpRequest) {
        String bgmToken = CONFIG.getBgmToken();
        if (StrUtil.isNotBlank(bgmToken)) {
            httpRequest.header(Header.AUTHORIZATION, "Bearer " + bgmToken);
        }
        // Bangumi 对短时间内的高频请求有限制，统一在此做轻微抖动。
        ThreadUtil.sleep(RandomUtil.randomInt(500, 1000));
        return httpRequest;
    }

    /**
     * 当前账号信息。
     *
     * @return 账号信息
     */
    public static BgmMe me() {
        String bgmToken = CONFIG.getBgmToken();
        Assert.notBlank(bgmToken, "BgmToken 未填写");
        return setToken(HttpReq.get(CONFIG.getBgmApi() + "/v0/me"))
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    return GsonStatic.fromJson(res.body(), BgmMe.class);
                });
    }

    /**
     * 当前账号名称，缺失时退化为数字 ID。
     *
     * @return 用户名
     */
    public static String username() {
        BgmMe me = me();
        return Opt.of(me)
                .map(BgmMe::getUsername)
                .filter(Objects::nonNull)
                .filter(StrUtil::isNotBlank)
                .orElse(String.valueOf(me.getId()));
    }

    /**
     * Token 剩余有效期（天）。
     *
     * @return 天数，未配置时为 0
     */
    public static Integer getExpiresDays() {
        String bgmToken = CONFIG.getBgmToken();
        if (StrUtil.isBlank(bgmToken)) {
            return 0;
        }
        long expires = HttpReq.post("https://bgm.tv/oauth/token_status")
                .form("access_token", bgmToken)
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    return jsonObject.get("expires").getAsLong() * 1000L;
                });

        long now = System.currentTimeMillis();
        if (expires <= now) {
            return 0;
        }
        return Math.toIntExact(TimeUnit.MILLISECONDS.toDays(expires - now));
    }

    /**
     * 自动刷新 Token。
     * <p>
     * 仅在模式为 AUTO、剩余不足 3 天且 App 凭据齐全时刷新，避免无意义请求。
     */
    public static void refreshToken() {
        if (CONFIG.getBgmTokenType() != BgmTokenTypeEnum.AUTO) {
            return;
        }
        if (StrUtil.isBlank(CONFIG.getBgmToken())) {
            return;
        }
        if (getExpiresDays() >= 3) {
            return;
        }
        String appId = CONFIG.getBgmAppID();
        String appSecret = CONFIG.getBgmAppSecret();
        String refreshToken = CONFIG.getBgmRefreshToken();
        String redirectUri = CONFIG.getBgmRedirectUri();
        if (List.of(appId, appSecret, refreshToken, redirectUri).stream().anyMatch(StrUtil::isBlank)) {
            return;
        }

        Map<String, String> bodyMap = Map.of(
                "grant_type", "refresh_token",
                "client_id", appId,
                "client_secret", appSecret,
                "refresh_token", refreshToken,
                "redirect_uri", redirectUri
        );

        HttpReq.post("https://bgm.tv/oauth/access_token", bodyMap)
                .then(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    CONFIG.setBgmToken(jsonObject.get("access_token").getAsString())
                            .setBgmRefreshToken(jsonObject.get("refresh_token").getAsString());
                });

        ConfigUtil.sync();
        log.info("BgmToken 已自动刷新");
    }
}
