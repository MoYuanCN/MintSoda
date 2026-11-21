package mint.soda.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 通知状态的展示样式。
 * <p>
 * 用户可以在设置中调整表情、状态名称与事件模板；模板支持 {@code ${name}}、
 * {@code ${source}} 与订阅字段（如 {@code ${title}}、{@code ${subgroup}}）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "通知状态样式")
public class NotificationStatusStyle implements Serializable {
    @Schema(description = "表情")
    private String emoji;

    @Schema(description = "状态名称")
    private String label;

    @Schema(description = "事件模板")
    private String template;
}
