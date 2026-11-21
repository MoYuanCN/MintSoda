package mint.soda.enums;

import mint.soda.offset.AutoOffset;
import mint.soda.offset.BaseOffset;
import mint.soda.offset.BgmOffset;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum OffsetTypeEnum {
    AUTO_OFFSET(AutoOffset.class),
    BGM_OFFSET(BgmOffset.class);

    @Getter
    private final Class<? extends BaseOffset> baseOffsetClass;
}
