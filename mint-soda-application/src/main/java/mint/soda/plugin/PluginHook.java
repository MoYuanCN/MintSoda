package mint.soda.plugin;

import mint.soda.entity.Ani;
import mint.soda.entity.Item;
import mint.soda.plugin.api.DownloadInterceptor;
import mint.soda.plugin.api.DownloadListener;
import mint.soda.plugin.api.ItemFilter;
import mint.soda.plugin.api.ItemTransformer;
import mint.soda.plugin.api.TemplateVariableProvider;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     * 模板变量缓存：一次刷新中同一订阅会被反复重命名，避免每个条目都触发插件网络请求。
     * 有效期很短，配置变更后能立刻生效。
     */
    private static final long VARIABLE_CACHE_TTL = 5_000L;
    private static final int VARIABLE_CACHE_MAX = 1_024;
    private final Map<String, CachedVariables> variableCache = new ConcurrentHashMap<>();
    /**
     * 模板变量的合并结果会随有效期过期，但插件本身很少变化；
     * 记下扩展点集合的标识，配置或插件变化时直接丢弃旧缓存。
     */
    private volatile int providerSignature;

    private record CachedVariables(Map<String, String> values, long expireAt) {
    }

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

    /**
     * 依次执行条目改写器。
     * <p>
     * 只允许修改标题与字幕组；任一改写器异常只记录日志并沿用已有结果，
     * 保证插件问题不会阻断下载流程。
     *
     * @param ani   订阅
     * @param item  资源项
     * @return 可能已被修改的资源项
     */
    public Item transform(Ani ani, Item item) {
        List<ItemTransformer> transformers = pluginManager.itemTransformers();
        if (transformers.isEmpty()) {
            return item;
        }
        ItemFilter.SubscriptionSnapshot subscription = subscription(ani);
        ItemFilter.ItemSnapshot snapshot = item(item);
        for (ItemTransformer transformer : transformers) {
            try {
                ItemTransformer.Item result = transformer.transform(subscription, snapshot, Map.of());
                if (result == null) {
                    continue;
                }
                String title = StrUtil.blankToDefault(result.title(), snapshot.title());
                String subgroup = StrUtil.blankToDefault(result.subgroup(), snapshot.subgroup());
                if (!title.equals(snapshot.title())) {
                    // 标题与重命名后的名称同步，避免后续流程仍使用旧标题。
                    item.setTitle(title);
                    if (StrUtil.isBlank(item.getReName()) || item.getReName().equals(snapshot.title())) {
                        item.setReName(title);
                    }
                }
                if (!subgroup.equals(snapshot.subgroup())) {
                    item.setSubgroup(subgroup);
                }
                snapshot = new ItemFilter.ItemSnapshot(title, item.getReName(), subgroup,
                        snapshot.episode(), snapshot.master(), snapshot.size(), snapshot.magnet());
            } catch (Exception e) {
                log.error("插件条目改写失败 {}", transformer.id());
                log.error(e.getMessage(), e);
            }
        }
        return item;
    }

    public void fire(PluginManager.SubscriptionEvent event, Ani ani) {
        if (ani == null) {
            return;
        }
        pluginManager.fireSubscriptionEvent(event, subscription(ani));
    }

    /**
     * 汇总插件提供的模板变量；单个插件异常不影响其他插件。
     */
    public Map<String, String> templateVariables(Ani ani) {
        List<TemplateVariableProvider> providers = pluginManager.variableProviders();
        if (providers.isEmpty() || ani == null) {
            return Map.of();
        }
        String cacheKey = String.join("|",
                StrUtil.nullToEmpty(ani.getId()),
                StrUtil.nullToEmpty(ani.getTitle()),
                StrUtil.nullToEmpty(ani.getSubgroup()),
                String.valueOf(ani.getSeason()));
        long now = System.currentTimeMillis();
        // 扩展点集合变化（启用/停用/重载插件）时先失效，避免继续命中旧值。
        int signature = providers.stream().mapToInt(provider -> System.identityHashCode(provider)).sum();
        if (signature != providerSignature) {
            providerSignature = signature;
            variableCache.clear();
        }
        CachedVariables cached = variableCache.get(cacheKey);
        if (cached != null && cached.expireAt() > now) {
            return cached.values();
        }
        if (variableCache.size() > VARIABLE_CACHE_MAX) {
            // 先清理过期项，全部未过期时再整体清空，避免缓存无限增长。
            variableCache.entrySet().removeIf(entry -> entry.getValue().expireAt() <= now);
            if (variableCache.size() > VARIABLE_CACHE_MAX) {
                variableCache.clear();
            }
        }

        ItemFilter.SubscriptionSnapshot subscription = subscription(ani);
        Map<String, String> result = new LinkedHashMap<>();
        for (TemplateVariableProvider provider : providers) {
            try {
                Map<String, String> variables = provider.variables(subscription);
                if (variables != null) {
                    result.putAll(variables);
                }
            } catch (Exception e) {
                log.error("插件模板变量提供方失败 {}", provider.id());
                log.error(e.getMessage(), e);
            }
        }
        Map<String, String> values = Map.copyOf(result);
        variableCache.put(cacheKey, new CachedVariables(values, now + VARIABLE_CACHE_TTL));
        return values;
    }

    /**
     * 下载拦截结果：{@code allowed=false} 表示插件要求取消下载。
     *
     * @param allowed  是否继续下载
     * @param savePath 最终保存位置
     * @param name     最终名称
     */
    public record InterceptResult(boolean allowed, String savePath, String name) {
        static InterceptResult allow(String savePath, String name) {
            return new InterceptResult(true, savePath, name);
        }

        static InterceptResult cancel() {
            return new InterceptResult(false, "", "");
        }
    }

    /**
     * 依次执行下载拦截器。
     * <p>
     * 没有拦截器时沿用原始值；任一拦截器返回 {@code null} 即取消本次下载；
     * 单个拦截器异常只记录日志并继续。
     *
     * @param ani      订阅
     * @param item     资源项
     * @param savePath 原始保存位置
     * @param name     原始名称
     */
    public InterceptResult intercept(Ani ani, Item item, String savePath, String name) {
        List<DownloadInterceptor> interceptors = pluginManager.downloadInterceptors();
        if (interceptors.isEmpty()) {
            return InterceptResult.allow(savePath, name);
        }
        String subgroup = StrUtil.blankToDefault(item.getSubgroup(), "未知字幕组");
        DownloadInterceptor.DownloadCandidate candidate = new DownloadInterceptor.DownloadCandidate(
                StrUtil.nullToEmpty(item.getInfoHash()),
                StrUtil.blankToDefault(name, item.getReName()),
                StrUtil.nullToEmpty(item.getTitle()),
                subgroup,
                item.getEpisode(),
                savePath,
                List.of(subgroup),
                StrUtil.nullToEmpty(item.getTorrent()),
                item.getLength(),
                subscription(ani)
        );
        for (DownloadInterceptor interceptor : interceptors) {
            try {
                DownloadInterceptor.DownloadCandidate result = interceptor.intercept(candidate, Map.of());
                if (result == null) {
                    if (!pluginManager.cancelAllowed(interceptor.id())) {
                        // 没有取消权限时按“不做修改”处理，避免插件借返回值绕过授权。
                        log.warn("插件拦截器 {} 未获取消权限，已忽略本次取消", interceptor.id());
                        continue;
                    }
                    log.debug("插件 {} 取消下载 {}", interceptor.id(), candidate.title());
                    return InterceptResult.cancel();
                }
                candidate = result;
            } catch (Exception e) {
                log.error("插件下载拦截失败 {}", interceptor.id());
                log.error(e.getMessage(), e);
            }
        }
        return InterceptResult.allow(
                StrUtil.blankToDefault(candidate.savePath(), savePath),
                StrUtil.blankToDefault(candidate.name(), name));
    }

    /**
     * 下载生命周期事件，供插件观察任务结果。
     */
    public enum DownloadEvent {
        ADDED, COMPLETED, FAILED
    }

    /**
     * 通知所有下载监听器。
     * <p>
     * 监听器不能修改任务，单个监听器异常只记录日志，不影响下载主流程。
     *
     * @param event  事件类型
     * @param ani    订阅
     * @param task   任务快照
     * @param context 额外上下文
     */
    public void downloadEvent(DownloadEvent event, Ani ani, DownloadListener.DownloadSnapshot task,
                              Map<String, Object> context) {
        List<DownloadListener> listeners = pluginManager.downloadListeners();
        if (listeners.isEmpty()) {
            return;
        }
        for (DownloadListener listener : listeners) {
            try {
                switch (event) {
                    case ADDED -> listener.onAdded(task, context);
                    case COMPLETED -> listener.onCompleted(task, context);
                    case FAILED -> listener.onFailed(task, context);
                }
            } catch (Exception e) {
                log.error("插件下载事件处理失败 {}", listener.id());
                log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * 构造下载事件所需的快照，缺省字段统一为空值。
     *
     * @param ani      订阅
     * @param hash     种子哈希
     * @param name     名称
     * @param title    原始标题
     * @param subgroup 字幕组
     * @param episode  集数
     * @param savePath 保存目录
     * @param tags     标签
     * @param size     体积
     */
    public DownloadListener.DownloadSnapshot downloadSnapshot(Ani ani, String hash, String name, String title,
                                                              String subgroup, Double episode, String savePath,
                                                              List<String> tags, Long size) {
        return new DownloadListener.DownloadSnapshot(
                StrUtil.nullToEmpty(hash),
                StrUtil.nullToEmpty(name),
                StrUtil.nullToEmpty(title),
                StrUtil.blankToDefault(subgroup, "未知字幕组"),
                episode,
                StrUtil.nullToEmpty(savePath),
                tags == null ? List.of() : tags,
                size,
                ani == null ? null : subscription(ani)
        );
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
