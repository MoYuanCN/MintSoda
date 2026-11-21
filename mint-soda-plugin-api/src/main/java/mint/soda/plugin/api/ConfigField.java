package mint.soda.plugin.api;

/**
 * 插件渠道/设置的配置字段描述。
 *
 * @param key         字段名
 * @param label       显示名
 * @param type        TEXT / PASSWORD / NUMBER / SWITCH / SELECT
 * @param placeholder 输入提示
 * @param required    是否必填
 * @param options     SELECT 的候选项
 */
public record ConfigField(
        String key,
        String label,
        FieldType type,
        String placeholder,
        boolean required,
        java.util.List<String> options
) {
    public ConfigField {
        options = options == null ? java.util.List.of() : java.util.List.copyOf(options);
    }

    public ConfigField(String key, String label, FieldType type) {
        this(key, label, type, "", false, java.util.List.of());
    }

    public enum FieldType {
        TEXT, PASSWORD, NUMBER, SWITCH, SELECT
    }
}
