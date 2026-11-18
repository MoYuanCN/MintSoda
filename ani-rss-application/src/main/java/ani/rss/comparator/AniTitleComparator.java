package ani.rss.comparator;

import ani.rss.entity.Ani;
import cn.hutool.core.util.StrUtil;

/**
 * 按标题排序，用于订阅页的字母顺序展示。
 */
public class AniTitleComparator implements AniComparator {
    @Override
    public int compare(Ani o1, Ani o2) {
        return StrUtil.compare(o1.getTitle(), o2.getTitle(), true);
    }
}
