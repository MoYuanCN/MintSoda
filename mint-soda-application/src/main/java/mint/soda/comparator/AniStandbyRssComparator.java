package mint.soda.comparator;

import mint.soda.entity.Ani;
import mint.soda.entity.StandbyRss;

import java.util.List;
import java.util.Objects;

/**
 * 按备用 RSS 数量倒序排序，数量相同时按标题拼音。
 */
public class AniStandbyRssComparator implements AniComparator {
    private static final AniPinyinComparator PINYIN = new AniPinyinComparator();

    @Override
    public int compare(Ani o1, Ani o2) {
        int result = Integer.compare(count(o2), count(o1));
        return result != 0 ? result : PINYIN.compare(o1, o2);
    }

    private int count(Ani ani) {
        List<StandbyRss> list = ani.getStandbyRssList();
        return Objects.isNull(list) ? 0 : list.size();
    }
}
