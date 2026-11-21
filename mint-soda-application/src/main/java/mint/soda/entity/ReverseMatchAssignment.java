package mint.soda.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 反匹配路由记录。
 * <p>
 * 反匹配订阅中，条目命中规则后会把目标字幕组写入这里，随订阅一起保存到
 * {@code data/subscriptions.json}。这样即使条目暂时从 RSS 中消失，已路由的集数仍能保留其字幕组，
 * 供重命名、下载标签与完结迁移使用。
 */
@Data
@Accessors(chain = true)
@Schema(description = "反匹配路由记录")
public class ReverseMatchAssignment implements Serializable {
    /**
     * 集数
     */
    @Schema(description = "集数")
    private Double episode;

    /**
     * 目标字幕组
     */
    @Schema(description = "目标字幕组")
    private String subgroup;

    /**
     * 命中时的标题
     */
    @Schema(description = "命中时的标题")
    private String title;

    /**
     * 记录时间戳
     */
    @Schema(description = "记录时间戳")
    private Long updatedAt;
}
