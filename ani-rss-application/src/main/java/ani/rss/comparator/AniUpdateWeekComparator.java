package ani.rss.comparator;

import ani.rss.entity.Ani;
import cn.hutool.core.date.DateUtil;

import java.util.Date;

/**
 * 按更新星期排序，从今天所在星期开始向后排列。
 */
public class AniUpdateWeekComparator implements AniComparator {
    @Override
    public int compare(Ani o1, Ani o2) {
        return Integer.compare(weekOffset(o1), weekOffset(o2));
    }

    private int weekOffset(Ani ani) {
        Date releaseDate = ani.getReleaseDate();
        if (releaseDate == null) {
            return Integer.MAX_VALUE;
        }
        int today = DateUtil.dayOfWeek(new Date()) - 1;
        int week = DateUtil.dayOfWeek(releaseDate) - 1;
        return (week - today + 7) % 7;
    }
}
