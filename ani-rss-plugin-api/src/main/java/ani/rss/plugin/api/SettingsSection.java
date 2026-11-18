package ani.rss.plugin.api;

import java.util.List;

/**
 * 插件设置页区块。前端按 fields 渲染表单，值以 JSON 保存。
 *
 * @param id          区块标识
 * @param title       标题
 * @param description 说明
 * @param fields      字段
 */
public record SettingsSection(
        String id,
        String title,
        String description,
        List<ConfigField> fields
) {
    public SettingsSection {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
