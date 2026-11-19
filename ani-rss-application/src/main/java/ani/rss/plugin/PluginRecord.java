package ani.rss.plugin;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件持久化记录，保存在 plugins/installed.json。
 */
@Data
@Accessors(chain = true)
public class PluginRecord implements Serializable {
    private String id;
    private Boolean enable;
    private String source;
    private Long installedAt;
    private String sha256;

    /**
     * 插件配置 JSON，由插件通过 PluginContext 读写
     */
    private String config;

    /**
     * 用户为插件授予的权限标识；为空表示尚未配置，由安装时按声明初始化
     */
    private List<String> grantedPermissions;

    private String latestVersion;
    private Boolean updateAvailable;
    private Long lastCheckTime;
}
