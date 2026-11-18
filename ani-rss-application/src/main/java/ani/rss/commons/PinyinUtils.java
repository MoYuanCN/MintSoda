package ani.rss.commons;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.pinyin.PinyinEngine;
import cn.hutool.extra.pinyin.engine.tinypinyin.TinyPinyinEngine;
import com.github.promeg.pinyinhelper.Pinyin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PinyinUtils {

    public static final PinyinEngine ENGINE;

    static {
        Pinyin.Config pinyinConfig = Pinyin.newConfig();
        pinyinConfig.with(new CustomPinyinMapDict());
        ENGINE = new TinyPinyinEngine(pinyinConfig);
    }

    public static PinyinEngine getEngine() {
        return ENGINE;
    }

    public static String getPinyin(String str) {
        return getPinyin(str, StrUtil.SPACE);
    }

    /**
     * 订阅列表每次请求都会重算拼音，标题通常不变，缓存最近的结果避免重复转换。
     */
    private static final Map<String, String> PINYIN_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, String> FIRST_LETTER_CACHE = new ConcurrentHashMap<>();

    private static final int CACHE_LIMIT = 2048;

    public static String getPinyin(String str, String separator) {
        if (StrUtil.isBlank(str)) {
            return str;
        }
        return cached(PINYIN_CACHE, "pinyin|" + separator + "|" + str,
                () -> getEngine().getPinyin(str, separator));
    }

    public static String getFirstLetter(String str, String separator) {
        if (StrUtil.isBlank(str)) {
            return str;
        }
        return cached(FIRST_LETTER_CACHE, "first|" + separator + "|" + str,
                () -> getEngine().getFirstLetter(str, separator));
    }

    private static String cached(Map<String, String> cache, String key, Supplier<String> supplier) {
        String value = cache.get(key);
        if (value != null) {
            return value;
        }
        value = supplier.get();
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(key, value);
        return value;
    }


    /**
     * 拼音首字母
     *
     * @param str 文字
     * @return 首字母
     */
    public static String getPinyinInitialLetters(String str) {
        if (StrUtil.isBlank(str)) {
            return "0";
        }
        // 只需要首字母，转换整段标题没有意义。
        char c = Character.toUpperCase(getEngine().getFirstLetter(str.charAt(0)));
        if (c >= '0' && c <= '9') {
            return "0";
        }
        if (c >= 'A' && c <= 'Z') {
            return String.valueOf(c);
        }
        return "#";
    }
}
