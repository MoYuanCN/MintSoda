package ani.rss.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 从链接安装插件的请求
 */
@Data
@Accessors(chain = true)
@Schema(description = "插件安装请求")
public class PluginInstallDTO implements Serializable {
    @Schema(description = "插件包地址")
    private String url;

    @Schema(description = "插件包 sha256，可空")
    private String sha256;

    @Schema(description = "插件包字节数，可空")
    private String size;
}
