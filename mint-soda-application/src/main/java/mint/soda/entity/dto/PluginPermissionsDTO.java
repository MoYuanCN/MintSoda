package mint.soda.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 插件权限保存请求
 */
@Data
@Accessors(chain = true)
@Schema(description = "插件权限保存请求")
public class PluginPermissionsDTO implements Serializable {
    @Schema(description = "插件标识")
    private String id;

    @Schema(description = "授予的权限标识")
    private List<String> permissions;
}
