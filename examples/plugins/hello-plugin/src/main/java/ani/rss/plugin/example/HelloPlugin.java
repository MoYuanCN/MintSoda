package ani.rss.plugin.example;

import ani.rss.plugin.api.ConfigField;
import ani.rss.plugin.api.DownloadInterceptor;
import ani.rss.plugin.api.ItemFilter;
import ani.rss.plugin.api.ItemTransformer;
import ani.rss.plugin.api.NotificationChannel;
import ani.rss.plugin.api.NotificationPayload;
import ani.rss.plugin.api.Plugin;
import ani.rss.plugin.api.PluginContext;
import ani.rss.plugin.api.PluginEndpoint;
import ani.rss.plugin.api.PluginPage;
import ani.rss.plugin.api.SettingsSection;
import ani.rss.plugin.api.TemplateVariableProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        context.registerTemplateVariableProvider(new TemplateVariableProvider() {
            @Override
            public String id() {
                return "hello-variables";
            }

            @Override
            public Map<String, String> variables(ItemFilter.SubscriptionSnapshot ani) {
                // 模板中可以写 ${helloSubgroup}，值来自插件计算。
                return Map.of("helloSubgroup", "插件-" + ani.group());
            }
        });

        context.registerDownloadInterceptor(new DownloadInterceptor() {
            @Override
            public String id() {
                return "hello-download";
            }

            @Override
            public DownloadCandidate intercept(DownloadCandidate candidate, Map<String, Object> ctx) {
                // 演示改写：只读透传，实际插件可返回 withSavePath / withName，或返回 null 取消。
                return candidate;
            }
        });

        context.registerItemTransformer(new ItemTransformer() {
            @Override
            public String id() {
                return "hello-transform";
            }

            @Override
            public Item transform(ItemFilter.SubscriptionSnapshot ani, ItemFilter.ItemSnapshot item,
                                  Map<String, Object> ctx) {
                // 返回 null 表示不修改；示例只在标题含 [简日] 时改写字幕组。
                if (item.title() != null && item.title().contains("[简日]")) {
                    return Item.subgroup("简日双语");
                }
                return null;
            }
        });

        // 插件自有页面：宿主按白名单区块渲染，插件只提供结构化描述。
        context.registerPage(new PluginPage(
                "dashboard",
                "示例页面",
                "DataLine",
                "演示文本、表格、表单与操作按钮四种区块。",
                List.of(
                        PluginPage.Block.text("说明", "表格数据来自 records 接口，表单提交到 save 接口。"),
                        PluginPage.Block.table("最近记录", "records", List.of(
                                new PluginPage.Block.Column("time", "时间"),
                                new PluginPage.Block.Column("title", "内容"))),
                        PluginPage.Block.form("页面设置", "save", List.of(
                                new ConfigField("endpoint", "接收地址", ConfigField.FieldType.TEXT,
                                        "https://example.com/hook", false, List.of()),
                                new ConfigField("enabled", "启用", ConfigField.FieldType.SWITCH))),
                        PluginPage.Block.actions("操作", List.of(
                                PluginPage.Block.Action.confirm("rescan", "重新扫描", "status",
                                        "确定要重新扫描吗？"))),
                        PluginPage.Block.divider()
                )
        ));

        context.registerEndpoint(new PluginEndpoint() {
            @Override
            public String path() {
                return "records";
            }

            @Override
            public Set<String> methods() {
                return Set.of("GET");
            }

            @Override
            public String handle(EndpointRequest request) {
                return "[{\"time\":\"示例\",\"title\":\"示例记录\"}]";
            }
        });

        context.registerEndpoint(new PluginEndpoint() {
            @Override
            public String path() {
                return "save";
            }

            @Override
            public Set<String> methods() {
                return Set.of("POST");
            }

            @Override
            public String handle(EndpointRequest request) {
                return "{\"ok\":true}";
            }
        });

        context.registerEndpoint(new PluginEndpoint() {
            @Override
            public String path() {
                return "status";
            }

            @Override
            public Set<String> methods() {
                return Set.of("GET", "POST");
            }

            @Override
            public String handle(EndpointRequest request) {
                return "{\"ok\":true,\"plugin\":\"hello-plugin\"}";
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
