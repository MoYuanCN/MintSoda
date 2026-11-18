package ani.rss.plugin;

import ani.rss.entity.Ani;
import ani.rss.entity.Item;
import ani.rss.plugin.api.ItemFilter;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 把宿主对象转换为插件快照，并执行插件过滤器。
 * <p>
 * 插件过滤在 {@code MatchRuleUtil} 之后执行，命中任一 {@code REJECT} 即拒绝，
 * 不会绕过已有的匹配、排除与反匹配优先级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginHook {

    private final PluginManager pluginManager;

    /**
     * 静态工具类通过 PluginHooks 访问同一实例。
     * 必须在字段注入完成后再绑定，否则会拿到未初始化依赖的对象。
     */
    @jakarta.annotation.PostConstruct
    void bindStaticBridge() {
        PluginHooks.bind(this);
    }

    public boolean accepts(Ani ani, Item item, Map<String, Object> context) {
        List<ItemFilter> filters = pluginManager.itemFilters();
        if (filters.isEmpty()) {
            return true;
        }
        ItemFilter.SubscriptionSnapshot subscription = subscription(ani);
        ItemFilter.ItemSnapshot snapshot = item(item);
        for (ItemFilter filter : filters) {
            try {
                ItemFilter.Decision decision = filter.decide(subscription, snapshot, context);
                if (decision == ItemFilter.Decision.REJECT) {
                    log.debug("插件 {} 拒绝条目 {}", filter.id(), snapshot.title());
                    return false;
                }
            } catch (Exception e) {
                log.error("插件过滤器失败 {}", filter.id());
                log.error(e.getMessage(), e);
            }
        }
        return true;
    }

    public boolean accepts(Ani ani, String title, String subgroup) {
        List<ItemFilter> filters = pluginManager.itemFilters();
        if (filters.isEmpty()) {
            return true;
        }
        ItemFilter.SubscriptionSnapshot subscription = subscription(ani);
        ItemFilter.ItemSnapshot snapshot = new ItemFilter.ItemSnapshot(
                title, title, StrUtil.nullToEmpty(subgroup), null, true, null, null);
        for (ItemFilter filter : filters) {
            try {
                if (filter.decide(subscription, snapshot, Map.of()) == ItemFilter.Decision.REJECT) {
                    return false;
                }
            } catch (Exception e) {
                log.error("插件过滤器失败 {}", filter.id());
                log.error(e.getMessage(), e);
            }
        }
        return true;
    }

    public void fire(PluginManager.SubscriptionEvent event, Ani ani) {
        if (ani == null) {
            return;
        }
        pluginManager.fireSubscriptionEvent(event, subscription(ani));
    }

    public ItemFilter.SubscriptionSnapshot subscription(Ani ani) {
        return new ItemFilter.SubscriptionSnapshot(
                StrUtil.nullToEmpty(ani.getId()),
                StrUtil.nullToEmpty(ani.getTitle()),
                StrUtil.nullToEmpty(ani.getSubgroup()),
                ani.getSeason(),
                StrUtil.nullToEmpty(ani.getWeekLabel()),
                ani.getCurrentEpisodeNumber(),
                ani.getTotalEpisodeNumber(),
                ani.getCustomTags() == null ? List.of() : ani.getCustomTags()
        );
    }

    public ItemFilter.ItemSnapshot item(Item item) {
        return new ItemFilter.ItemSnapshot(
                StrUtil.nullToEmpty(item.getTitle()),
                StrUtil.nullToEmpty(item.getReName()),
                StrUtil.nullToEmpty(item.getSubgroup()),
                item.getEpisode(),
                Boolean.TRUE.equals(item.getMaster()),
                item.getLength(),
                StrUtil.nullToEmpty(item.getTorrent())
        );
    }
}
