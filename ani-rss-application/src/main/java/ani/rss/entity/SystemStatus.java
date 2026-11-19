package ani.rss.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 运行状态概览，供 WebUI 与外部监控读取。
 */
@Data
@Accessors(chain = true)
@Schema(description = "运行状态概览")
public class SystemStatus implements Serializable {
    @Schema(description = "版本")
    private String version;

    @Schema(description = "运行时长（毫秒）")
    private Long uptime;

    @Schema(description = "订阅总数")
    private Integer subscriptions;

    @Schema(description = "已启用订阅数")
    private Integer enabledSubscriptions;

    @Schema(description = "堆内存已用字节")
    private Long heapUsed;

    @Schema(description = "堆内存最大字节")
    private Long heapMax;

    @Schema(description = "活跃线程数")
    private Integer threads;

    @Schema(description = "处理器核数")
    private Integer processors;

    @Schema(description = "系统负载，不可用时为 -1")
    private Double loadAverage;
}
