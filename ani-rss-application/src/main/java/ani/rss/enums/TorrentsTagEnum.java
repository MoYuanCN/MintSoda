package ani.rss.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TorrentsTagEnum {
    ANI_RSS("ani-rss-Yuri"),
    RENAME("RENAME"),
    STANDBY_RSS("备用RSS"),
    DOWNLOAD_COMPLETE("下载完成");

    private final String value;

    /**
     * 旧版本写入的分类与标签。
     * <p>
     * 升级后新任务使用新标识，历史任务仍是旧值，过滤时需要同时识别，
     * 否则已完成的任务会从列表中消失，重命名与完成通知也会失效。
     */
    public static final String LEGACY_ANI_RSS = "ani-rss";

    /**
     * 分类或标签是否由本应用写入。
     *
     * @param value 分类或标签
     * @return 是否命中
     */
    public static boolean matches(String value) {
        return ANI_RSS.value.equals(value) || LEGACY_ANI_RSS.equals(value);
    }
}
