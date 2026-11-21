package mint.soda;

import mint.soda.commons.StoragePaths;
import mint.soda.entity.Global;
import mint.soda.util.other.ConfigUtil;
import mint.soda.util.other.SystemTrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.Security;
import java.util.List;
import java.util.Map;

@EnableScheduling
@SpringBootApplication
public class MintSodaApplication {

    public static void main(String[] args) {
        Global.ARGS = List.of(ObjectUtil.defaultIfNull(args, new String[]{}));
        loadProperty();
        prepareStorage();
        SystemTrayUtil.start();
        SpringApplication.run(MintSodaApplication.class, args);
    }

    /**
     * 绑定存储目录并确认可写。
     * <p>
     * 程序目录下的 {@code config/} 或 {@code data/} 常由容器或隔离环境挂载进来，
     * 只读或属主不匹配时在这里直接给出结论，而不是等到运行中写盘失败。
     */
    private static void prepareStorage() {
        try {
            ConfigUtil.getConfigDir();
            StoragePaths.checkWritable();
        } catch (Exception e) {
            System.err.println("启动失败: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void loadProperty() {
        // 隐藏dock栏图标
        System.setProperty("apple.awt.UIElement", "true");
        // 菜单栏图标颜色自适应
        System.setProperty("apple.awt.enableTemplateImages", "true");
        // 启用Basic认证
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        // DNS解析成功过期时间
        Security.setProperty("networkaddress.cache.ttl", "30");
        // DNS解析失败过期时间
        Security.setProperty("networkaddress.cache.negative.ttl", "5");

        // 处理命令行参数
        Map<String, String> mapping = Map.of(
                "--config", "CONFIG",
                "--mcp-enabled", "MCP_ENABLED",
                "--swagger-enabled", "SWAGGER_ENABLED"
        );

        // --setup 是开关式参数，其余参数使用 --name=value 形式。
        if (Global.ARGS.contains("--setup")) {
            System.setProperty("SETUP", "true");
        }

        mapping.forEach((k, v) -> {
            for (String arg : Global.ARGS) {
                String separator = k + "=";
                if (arg.startsWith(separator)) {
                    String value = StrUtil.subAfter(arg, separator, false);
                    System.setProperty(v, value);
                    return;
                }
            }
        });
    }

}
