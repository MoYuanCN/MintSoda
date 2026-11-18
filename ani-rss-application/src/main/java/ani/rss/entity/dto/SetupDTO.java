package ani.rss.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 首次启动设置账号的请求体。
 * <p>
 * 该接口只允许本机或内网调用，因此接收明文密码以便服务端校验强度，
 * 落库时只保存摘要。
 */
@Data
@Accessors(chain = true)
@Schema(description = "首次启动设置账号")
public class SetupDTO implements Serializable {
    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;
}
