package ani.rss.util.other;

import ani.rss.entity.Ani;
import ani.rss.plugin.PluginHooks;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import wushuo.tmdb.api.entity.Tmdb;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 模板占位符的统一替换。
 * <p>
 * 通知模板、重命名模板与下载路径模板都需要同一批字段，过去各写一份替换逻辑，
 * 容易在新增字段时遗漏其中一处。这里集中生成字段值，取值昂贵的字段按需解析。
 */
public final class TemplateVariables {

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, Supplier<String>> lazy = new LinkedHashMap<>();
    private final Map<String, String> resolved = new LinkedHashMap<>();

    private TemplateVariables() {
    }

    /**
     * 从订阅生成常用字段：标题、字幕组、季、TMDB ID 与放送日期。
     * <p>
     * {@code ${jpTitle}} 与 {@code ${bgmId}} 需要联网解析，仅在模板真正引用时才计算。
     */
    public static TemplateVariables from(Ani ani) {
        TemplateVariables variables = new TemplateVariables();
        if (ani == null) {
            return variables;
        }
        int season = ObjectUtil.defaultIfNull(ani.getSeason(), 0);
        variables.put("title", StrUtil.nullToEmpty(ani.getTitle()));
        variables.put("subgroup", StrUtil.nullToEmpty(ani.getSubgroup()));
        variables.put("season", String.valueOf(season));
        variables.put("seasonFormat", String.format("%02d", season));
        variables.put("tmdbid", Optional.ofNullable(ani.getTmdb())
                .map(Tmdb::getId)
                .filter(StrUtil::isNotBlank)
                .orElse(""));

        Date releaseDate = ani.getReleaseDate();
        if (releaseDate != null) {
            variables.put("year", String.valueOf(DateUtil.year(releaseDate)));
            variables.put("month", String.valueOf(DateUtil.month(releaseDate) + 1));
            variables.put("date", String.valueOf(DateUtil.dayOfMonth(releaseDate)));
        }

        variables.lazy("jpTitle", () -> RenameUtil.getJpTitle(ani));
        variables.lazy("bgmId", () -> BgmUtil.getSubjectId(ani));

        // 插件可以追加或覆盖字段，供自定义模板使用。
        PluginHooks.templateVariables(ani).forEach(variables::put);
        return variables;
    }

    /**
     * 设置固定字段值，会覆盖同名的自动字段。
     */
    public TemplateVariables put(String key, String value) {
        if (StrUtil.isNotBlank(key)) {
            values.put(key, StrUtil.nullToEmpty(value));
        }
        return this;
    }

    /**
     * 注册按需计算的字段。
     */
    public TemplateVariables lazy(String key, Supplier<String> supplier) {
        if (StrUtil.isNotBlank(key) && supplier != null) {
            lazy.put(key, supplier);
        }
        return this;
    }

    /**
     * 只替换模板中真实出现的占位符，未出现的字段不会被计算。
     */
    public String apply(String template) {
        if (StrUtil.isBlank(template)) {
            return StrUtil.nullToEmpty(template);
        }
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = replace(result, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Supplier<String>> entry : lazy.entrySet()) {
            String placeholder = placeholder(entry.getKey());
            if (!result.contains(placeholder)) {
                continue;
            }
            String value = resolved.computeIfAbsent(entry.getKey(), key -> {
                try {
                    return StrUtil.nullToEmpty(entry.getValue().get());
                } catch (Exception e) {
                    return "";
                }
            });
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private static String replace(String template, String key, String value) {
        String placeholder = placeholder(key);
        return template.contains(placeholder) ? template.replace(placeholder, value) : template;
    }

    private static String placeholder(String key) {
        return "${" + key + "}";
    }
}
