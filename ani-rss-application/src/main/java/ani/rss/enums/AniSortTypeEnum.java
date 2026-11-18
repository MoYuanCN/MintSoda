package ani.rss.enums;

import ani.rss.comparator.AniComparator;
import ani.rss.comparator.AniDownloadTimeComparator;
import ani.rss.comparator.AniPinyinComparator;
import ani.rss.comparator.AniScoreComparator;
import ani.rss.comparator.AniStandbyRssComparator;
import ani.rss.comparator.AniTitleComparator;
import ani.rss.comparator.AniUpdateWeekComparator;
import cn.hutool.core.util.ReflectUtil;

/**
 * 排序方式
 */
public enum AniSortTypeEnum {
    /**
     * 评分
     */
    SCORE(AniScoreComparator.class),
    /**
     * 拼音
     */
    PINYIN(AniPinyinComparator.class),
    /**
     * 最后下载时间
     */
    DOWNLOAD_TIME(AniDownloadTimeComparator.class),
    /**
     * 更新星期
     */
    UPDATE_WEEK(AniUpdateWeekComparator.class),
    /**
     * 备用 RSS 数量
     */
    STANDBY_RSS(AniStandbyRssComparator.class),
    /**
     * 标题
     */
    TITLE(AniTitleComparator.class);

    AniSortTypeEnum(Class<? extends AniComparator> aClass) {
        this.comparator = ReflectUtil.newInstance(aClass);
    }

    public final AniComparator comparator;
}
