package ani.rss.plugin.example;

import ani.rss.plugin.api.ConfigField;
import ani.rss.plugin.api.ItemFilter;
import ani.rss.plugin.api.NotificationChannel;
import ani.rss.plugin.api.NotificationPayload;
import ani.rss.plugin.api.Plugin;
import ani.rss.plugin.api.PluginContext;
import ani.rss.plugin.api.SettingsSection;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 示例插件：注册一个通知渠道、一个过滤器和一组设置项。
 */
public class HelloPlugin implements Plugin {

    private PluginContext context;

    @Override
    public void onLoad(PluginContext context) {
        this.context = context;
        context.log("示例插件已加载");
    }

    @Override
    public void onEnable(PluginContext context) {
        context.registerNotificationChannel(new NotificationChannel() {
            @Override
            public String id() {
                return "hello-channel";
            }

            @Override
            public String name() {
                return "示例通知渠道";
            }

            @Override
            public List<ConfigField> fields() {
                return List.of(
                        new ConfigField("endpoint", "接收地址", ConfigField.FieldType.TEXT,
                                "https://example.com/hook", true, List.of()),
                        new ConfigField("token", "令牌", ConfigField.FieldType.PASSWORD)
                );
            }

            @Override
            public void send(NotificationPayload payload, Map<String, String> options) {
                // 实际插件在这里发起 HTTP 请求；示例只记录日志。
                context.log("发送通知 " + payload.status() + " " + payload.title());
            }
        });

        context.registerItemFilter(new ItemFilter() {
            @Override
            public String id() {
                return "hello-filter";
            }

            @Override
            public Decision decide(SubscriptionSnapshot ani, ItemSnapshot item, Map<String, Object> ctx) {
                // 只演示能力：不做拦截。
                return Decision.ABSTAIN;
            }
        });

        context.registerScheduledTask(new ani.rss.plugin.api.ScheduledPluginTask() {
            @Override
            public String id() {
                return "hello-task";
            }

            @Override
            public Duration interval() {
                return Duration.ofMinutes(30);
            }

            @Override
            public void run(PluginContext context) {
                context.log("示例周期任务执行");
            }
        });

        context.registerSettingsSection(new SettingsSection(
                "hello-settings",
                "示例设置",
                "插件会把这些字段以 JSON 保存在插件数据目录。",
                List.of(
                        new ConfigField("enabled", "启用附加行为", ConfigField.FieldType.SWITCH),
                        new ConfigField("mode", "模式", ConfigField.FieldType.SELECT,
                                "", false, List.of("A", "B"))
                )
        ));
    }

    @Override
    public void onDisable(PluginContext context) {
        context.log("示例插件已停用");
    }
}
