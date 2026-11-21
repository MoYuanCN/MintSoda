package mint.soda.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 插件配置保存请求
 */
@Data
@Accessors(chain = true)
@Schema(description = "插件配置保存请求")
public class PluginConfigDTO implements Serializable {
    @Schema(description = "插件标识")
    private String id;

    @Schema(description = "配置 JSON")
    private String config;
}
