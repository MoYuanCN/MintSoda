package ani.rss.plugin.api;

import java.util.List;
import java.util.Map;

/**
 * 条目改写：在宿主匹配规则与过滤之后、重命名之前调整条目的标题或字幕组。
 * <p>
 * 与 {@link ItemFilter} 不同，这里不能拒绝条目，只能返回修改后的快照；
 * 返回 {@code null} 表示不修改。改写字幕组会影响重命名、下载标签与本地目录，
 * 但不会改变订阅本身的字幕组设置。
 * <p>
 * 顺序在 {@code MatchRuleUtil} 与插件过滤器之后，因此不会绕过反匹配 &gt; 全局匹配 &gt; 排除的优先级。
 */
public interface ItemTransformer {

    /**
     * 改写器标识
     */
    String id();

    /**
     * 优先级，数值越小越先执行
     */
    default int order() {
        return 100;
    }

    /**
     * 改写单条资源。
     *
     * @param ani     订阅快照
     * @param item    资源快照
     * @param context 额外上下文
     * @return 修改后的快照，或 {@code null} 表示不修改
     */
    Item transform(ItemFilter.SubscriptionSnapshot ani, ItemFilter.ItemSnapshot item, Map<String, Object> context);

    /**
     * 改写结果。只有标题与字幕组可以修改，其余字段仅为上下文。
     *
     * @param title    新的原始标题，留空表示保持原值
     * @param subgroup 新的字幕组，留空表示保持原值
     */
    record Item(String title, String subgroup) {
        public static Item subgroup(String subgroup) {
            return new Item("", subgroup);
        }

        public static Item title(String title) {
            return new Item(title, "");
        }
    }

    /**
     * 常用改写器列表
     */
    static List<String> ids(List<ItemTransformer> transformers) {
        return transformers == null ? List.of() : transformers.stream().map(ItemTransformer::id).toList();
    }
}
