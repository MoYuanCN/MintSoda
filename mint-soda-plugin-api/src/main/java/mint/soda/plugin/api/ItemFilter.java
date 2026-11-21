package mint.soda.plugin.api;

import java.util.List;
import java.util.Map;

/**
 * 订阅过滤器，在宿主匹配规则之后执行。
 * <p>
 * 顺序固定为：反匹配 &gt; 全局匹配 &gt; 排除 &gt; 插件过滤。
 * 多个插件按优先级顺序执行，任一插件返回 {@link Decision#REJECT} 即拒绝。
 */
public interface ItemFilter {

    /**
     * 过滤器标识
     */
    String id();

    /**
     * 优先级，数值越小越先执行
     */
    default int order() {
        return 100;
    }

    /**
     * 判定单条资源
     *
     * @param ani     订阅快照
     * @param item    资源快照
     * @param context 额外上下文
     * @return 判定结果
     */
    Decision decide(SubscriptionSnapshot ani, ItemSnapshot item, Map<String, Object> context);

    /**
     * 过滤判定
     */
    enum Decision {
        /** 不干预 */
        ABSTAIN,
        /** 接受；可与宿主判定结果一起使用 */
        ACCEPT,
        /** 拒绝 */
        REJECT
    }

    /**
     * 订阅快照
     *
     * @param id         订阅 ID
     * @param title      标题
     * @param group      字幕组
     * @param season     季
     * @param weekLabel  放送星期
     * @param currentEpisode 当前集数
     * @param totalEpisode   总集数
     * @param tags       自定义标签
     */
    record SubscriptionSnapshot(
            String id,
            String title,
            String group,
            Integer season,
            String weekLabel,
            Integer currentEpisode,
            Integer totalEpisode,
            List<String> tags
    ) {
        public SubscriptionSnapshot {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    /**
     * 资源快照
     *
     * @param title    原始标题
     * @param rename   重命名后的标题
     * @param subgroup 字幕组
     * @param episode  集数
     * @param master   是否主 RSS
     * @param size     体积（字节），可为空
     * @param magnet   磁力链接，可为空
     */
    record ItemSnapshot(
            String title,
            String rename,
            String subgroup,
            Double episode,
            boolean master,
            Long size,
            String magnet
    ) {
    }
}
