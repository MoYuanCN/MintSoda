package ani.rss.plugin.api;

import java.util.List;

/**
 * 插件在主程序界面中展示的独立页面。
 * <p>
 * 页面由宿主按 {@link Block} 的白名单类型渲染，插件不提供 HTML 或脚本，
 * 因此不会把插件内容当作页面标记注入。需要动态数据时，页面区块通过 {@code source}
 * 指向插件自己的接口（{@code /api/plugin/api/{pluginId}/{path}}），宿主负责带登录态调用。
 *
 * @param id          页面标识，在同一插件内唯一
 * @param title       页面标题
 * @param icon        Element Plus 图标名称，留空使用默认图标
 * @param description 页面说明
 * @param blocks      页面区块，按顺序渲染
 */
public record PluginPage(
        String id,
        String title,
        String icon,
        String description,
        List<Block> blocks
) {
    public PluginPage {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public static PluginPage of(String id, String title, List<Block> blocks) {
        return new PluginPage(id, title, "", "", blocks);
    }

    /**
     * 页面区块，类型决定渲染方式与可用字段。
     */
    public record Block(
            Type type,
            String title,
            String text,
            List<ConfigField> fields,
            String source,
            List<Column> columns,
            List<Action> actions
    ) {
        public Block {
            fields = fields == null ? List.of() : List.copyOf(fields);
            columns = columns == null ? List.of() : List.copyOf(columns);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }

        public enum Type {
            /** 标题、段落或说明文字 */
            TEXT,
            /** 设置表单，提交后把字段值 POST 到 source */
            FORM,
            /** 数据表格，从 source 读取数组并渲染 */
            TABLE,
            /** 操作按钮组，每个按钮调用一个接口 */
            ACTIONS,
            /** 分隔线 */
            DIVIDER
        }

        public static Block text(String title, String text) {
            return new Block(Type.TEXT, title, text, List.of(), "", List.of(), List.of());
        }

        public static Block divider() {
            return new Block(Type.DIVIDER, "", "", List.of(), "", List.of(), List.of());
        }

        public static Block form(String title, String source, List<ConfigField> fields) {
            return new Block(Type.FORM, title, "", fields, source, List.of(), List.of());
        }

        public static Block table(String title, String source, List<Column> columns) {
            return new Block(Type.TABLE, title, "", List.of(), source, columns, List.of());
        }

        public static Block actions(String title, List<Action> actions) {
            return new Block(Type.ACTIONS, title, "", List.of(), "", List.of(), actions);
        }

        /**
         * 表格列
         *
         * @param key   数据字段名
         * @param label 表头
         */
        public record Column(String key, String label) {
        }

        /**
         * 操作按钮
         *
         * @param id          按钮标识
         * @param label       按钮文字
         * @param source      插件接口相对路径
         * @param method      请求方法，默认 POST
         * @param confirm     是否需要二次确认
         * @param confirmText 确认提示
         */
        public record Action(
                String id,
                String label,
                String source,
                String method,
                boolean confirm,
                String confirmText
        ) {
            public Action {
                method = method == null || method.isBlank() ? "POST" : method.toUpperCase(java.util.Locale.ROOT);
            }

            public static Action of(String id, String label, String source) {
                return new Action(id, label, source, "POST", false, "");
            }

            public static Action confirm(String id, String label, String source, String confirmText) {
                return new Action(id, label, source, "POST", true, confirmText);
            }
        }
    }
}
