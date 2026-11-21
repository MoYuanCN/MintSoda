package mint.soda.enums;

import mint.soda.comparator.AniComparator;
import mint.soda.comparator.AniDownloadTimeComparator;
import mint.soda.comparator.AniPinyinComparator;
import mint.soda.comparator.AniScoreComparator;
import mint.soda.comparator.AniStandbyRssComparator;
import mint.soda.comparator.AniTitleComparator;
import mint.soda.comparator.AniUpdateWeekComparator;
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
