package ani.rss.service;

import ani.rss.commons.GsonStatic;
import ani.rss.entity.Config;
import ani.rss.entity.MikanBgm;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CacheService {

    public Map<String, MikanBgm> getMikanBgm() {
        Config config = ConfigUtil.CONFIG;
        String outTradeNo = config.getOutTradeNo();
        if (StrUtil.isBlank(outTradeNo)) {
            return new HashMap<>();
        }

        Map<String, MikanBgm> mikanBgmMap = new HashMap<>();
        try {
            String url = StrFormatter.format(
                    "https://cache.wushuo.top/mikan/bgm/{}",
                    SecureUtil.sha256(outTradeNo)
            );
            Type type = new TypeToken<HashMap<String, MikanBgm>>() {
            }.getType();
            mikanBgmMap = HttpReq.get(url)
                    .timeout(1000 * 5)
                    .thenFunction(res -> {
                        int status = res.getStatus();
                        if (status == 404) {
                            return new HashMap<>();
                        }
                        HttpReq.assertStatus(res);
                        return GsonStatic.fromJson(res.body(), type);
                    });
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return mikanBgmMap;
    }
}
