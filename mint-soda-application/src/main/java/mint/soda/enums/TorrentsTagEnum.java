package mint.soda.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TorrentsTagEnum {
    MINT_SODA("MintSoda"),
    RENAME("RENAME"),
    STANDBY_RSS("备用RSS"),
    DOWNLOAD_COMPLETE("下载完成");

    private final String value;

    /**
     * 分类或标签是否由本应用写入。
     *
     * @param value 分类或标签
     * @return 是否命中
     */
    public static boolean matches(String value) {
        return MINT_SODA.value.equals(value);
    }
}
