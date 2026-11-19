package ani.rss.service.support;

import ani.rss.commons.FileUtils;
import ani.rss.commons.PinyinUtils;
import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.util.other.ConfigUtil;
import ani.rss.util.other.RenameUtil;
import ani.rss.util.other.TemplateVariables;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import wushuo.tmdb.api.entity.Tmdb;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 下载位置模板的解析。
 * <p>
 * 与上一版相比独立成类，下载流程只负责调度与落盘，模板规则集中在
 * {@link #resolve(Ani, Config)}：先确定模板（剧场版 / 自定义），再依次替换
 * 拼音首字母、季度与通用字段，最后交给 {@link TemplateVariables} 与重命名字段处理。
 * <p>
 * 结果会写入 {@link DownloadPass}，同一轮刷新内同一订阅只计算一次。
 */
public final class DownloadPathResolver {

    private DownloadPathResolver() {
    }

    /**
     * 使用当前配置解析下载位置。
     *
     * @param ani 订阅
     * @return 下载位置
     */
    public static String resolve(Ani ani) {
        return resolve(ani, ConfigUtil.CONFIG);
    }

    /**
     * 使用指定的模板解析下载位置，不修改订阅本身。
     *
     * @param ani                订阅
     * @param downloadPathTemplate 下载位置模板
     * @return 下载位置
     */
    public static String resolve(Ani ani, String downloadPathTemplate) {
        Ani clone = ObjectUtil.clone(ani);
        clone.setCustomDownloadPathTemplate(downloadPathTemplate)
                .setCustomDownloadPath(true);
        return resolve(clone, ConfigUtil.CONFIG);
    }

    /**
     * 使用指定配置解析下载位置。
     *
     * @param ani    订阅
     * @param config 配置
     * @return 下载位置
     */
    public static String resolve(Ani ani, Config config) {
        String cacheKey = null;
        // 只有走全局配置时结果才可复用，调用方传入的临时配置可能不同。
        if (DownloadPass.active() && config == ConfigUtil.CONFIG) {
            cacheKey = cacheKey(ani);
            String cached = DownloadPass.getDownloadPath(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        String downloadPathTemplate = pickTemplate(ani, config);

        String letter = PinyinUtils.getPinyinInitialLetters(StrUtil.trimToEmpty(ani.getTitle()));
        downloadPathTemplate = downloadPathTemplate.replace("${letter}", letter);

        Date releaseDate = ani.getReleaseDate();
        Date tmdbDate = Optional.ofNullable(ani.getTmdb())
                .map(Tmdb::getDate)
                .orElse(releaseDate);

        int tmdbYear = DateUtil.year(tmdbDate);
        int year = DateUtil.year(releaseDate);
        int month = DateUtil.month(releaseDate) + 1;
        String monthFormat = String.format("%02d", month);

        if (containsQuarterField(downloadPathTemplate)) {
            Quarter quarter = Quarter.of(month);
            if (month == 12) {
                // 使用季度信息时 12 月属于次年冬季，年份要跟着调整，避免年份与季度矛盾。
                year++;
            }
            downloadPathTemplate = downloadPathTemplate
                    .replace("${quarter}", String.valueOf(quarter.number))
                    .replace("${quarterFormat}", String.format("%02d", quarter.number))
                    .replace("${quarterName}", quarter.name);
        }

        // 年份可能因季度规则被调整，这里先写入本方法的计算值，再交给统一模板替换。
        TemplateVariables variables = TemplateVariables.from(ani)
                .put("tmdbYear", String.valueOf(tmdbYear))
                .put("year", String.valueOf(year))
                .put("month", String.valueOf(month))
                .put("monthFormat", monthFormat);
        downloadPathTemplate = variables.apply(downloadPathTemplate);

        List<Func1<Ani, Object>> list = List.of(Ani::getThemoviedbName);
        downloadPathTemplate = RenameUtil.replaceField(downloadPathTemplate, ani, list);

        String downloadPath = FileUtils.getAbsolutePath(downloadPathTemplate);
        if (cacheKey != null) {
            DownloadPass.putDownloadPath(cacheKey, downloadPath);
        }
        return downloadPath;
    }

    /**
     * 选择模板：剧场版优先用剧场版模板，自定义位置优先用自定义模板。
     */
    private static String pickTemplate(Ani ani, Config config) {
        String template = config.getDownloadPathTemplate();
        String ovaTemplate = config.getOvaDownloadPathTemplate();
        if (Boolean.TRUE.equals(ani.getOva()) && StrUtil.isNotBlank(ovaTemplate)) {
            template = ovaTemplate;
        }
        String customTemplate = ani.getCustomDownloadPathTemplate();
        if (Boolean.TRUE.equals(ani.getCustomDownloadPath()) && StrUtil.isNotBlank(customTemplate)) {
            template = StrUtil.split(customTemplate, "\n", true, true)
                    .stream()
                    .map(FileUtils::getAbsolutePath)
                    .findFirst()
                    .orElse(template);
        }
        return template;
    }

    private static boolean containsQuarterField(String template) {
        return template.contains("${quarter}")
                || template.contains("${quarterFormat}")
                || template.contains("${quarterName}");
    }

    private static String cacheKey(Ani ani) {
        return String.join("|",
                StrUtil.nullToEmpty(ani.getId()),
                String.valueOf(ani.getSeason()),
                StrUtil.nullToEmpty(ani.getTitle()),
                StrUtil.nullToEmpty(ani.getSubgroup()),
                String.valueOf(ani.getOva()),
                String.valueOf(ani.getReleaseDate()),
                String.valueOf(ani.getCustomDownloadPath()),
                StrUtil.nullToEmpty(ani.getCustomDownloadPathTemplate())
        );
    }

    /**
     * 季度归属。参考上游 #451：月底先行播放的番仍归入下一季，12 月归入次年冬季。
     */
    private record Quarter(int number, String name) {
        private static Quarter of(int month) {
            if (List.of(12, 1, 2).contains(month)) {
                return new Quarter(1, "冬");
            }
            if (List.of(3, 4, 5).contains(month)) {
                return new Quarter(4, "春");
            }
            if (List.of(6, 7, 8).contains(month)) {
                return new Quarter(7, "夏");
            }
            return new Quarter(10, "秋");
        }
    }
}
