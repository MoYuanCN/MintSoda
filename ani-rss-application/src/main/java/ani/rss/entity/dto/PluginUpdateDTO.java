package ani.rss.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 插件更新检查请求
 */
@Data
@Accessors(chain = true)
@Schema(description = "插件更新检查请求")
public class PluginUpdateDTO implements Serializable {
    @Schema(description = "插件标识")
    private String id;

    @Schema(description = "市场地址，可空")
    private String marketUrl;
}
