package ani.rss.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 插件市场地址请求
 */
@Data
@Accessors(chain = true)
@Schema(description = "插件市场地址请求")
public class PluginUrlDTO implements Serializable {
    @Schema(description = "地址，留空表示使用已保存的市场地址")
    private String url;
}
