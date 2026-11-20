package ani.rss.plugin;

import ani.rss.entity.Ani;
import ani.rss.entity.Item;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 静态工具类访问插件管道的桥接。
 * <p>
 * {@code ItemsUtil} 等静态工具不通过依赖注入获取 Bean，这里缓存 {@link PluginHook} 引用；
 * 未初始化或未启用插件时始终放行，保证宿主在没有插件时行为不变。
 */
@Slf4j
public final class PluginHooks {

    private static volatile PluginHook hook;

    private PluginHooks() {
    }

    public static void bind(PluginHook pluginHook) {
        hook = pluginHook;
    }

    private static PluginHook hook() {
        PluginHook current = hook;
        if (current != null) {
            return current;
        }
        try {
            current = SpringUtil.getBean(PluginHook.class);
            hook = current;
        } catch (Exception e) {
            return null;
        }
        return current;
    }

    /**
     * 插件过滤，异常时放行，避免插件问题阻断下载主流程。
     */
    public static boolean accepts(Ani ani, Item item) {
        PluginHook current = hook();
        if (current == null) {
            return true;
        }
        try {
            return current.accepts(ani, item, java.util.Map.of());
        } catch (Exception e) {
            log.error("插件过滤执行失败");
            log.error(e.getMessage(), e);
            return true;
        }
    }

    /**
     * 插件条目改写，失败时返回原始条目，不影响下载流程。
     */
    public static Item transform(Ani ani, Item item) {
        PluginHook current = hook();
        if (current == null) {
            return item;
        }
        try {
            return current.transform(ani, item);
        } catch (Exception e) {
            log.error("插件条目改写执行失败");
            log.error(e.getMessage(), e);
            return item;
        }
    }

    /**
     * 插件模板变量，失败时返回空 Map，模板保持内置字段。
     */
    public static java.util.Map<String, String> templateVariables(Ani ani) {
        PluginHook current = hook();
        if (current == null) {
            return java.util.Map.of();
        }
        try {
            return current.templateVariables(ani);
        } catch (Exception e) {
            log.error("插件模板变量执行失败");
            log.error(e.getMessage(), e);
            return java.util.Map.of();
        }
    }

    /**
     * 下载拦截，异常时放行并沿用原始保存位置。
     */
    public static PluginHook.InterceptResult intercept(Ani ani, Item item, String savePath, String name) {
        PluginHook current = hook();
        if (current == null) {
            return PluginHook.InterceptResult.allow(savePath, name);
        }
        try {
            return current.intercept(ani, item, savePath, name);
        } catch (Exception e) {
            log.error("插件下载拦截执行失败");
            log.error(e.getMessage(), e);
            return PluginHook.InterceptResult.allow(savePath, name);
        }
    }

    /**
     * 下载生命周期事件，异常时忽略，避免影响下载主流程。
     */
    public static void downloadEvent(PluginHook.DownloadEvent event, Ani ani,
                                     ani.rss.plugin.api.DownloadListener.DownloadSnapshot task) {
        PluginHook current = hook();
        if (current == null) {
            return;
        }
        try {
            current.downloadEvent(event, ani, task, java.util.Map.of());
        } catch (Exception e) {
            log.error("插件下载事件执行失败");
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 构造下载事件快照，供静态工具类在不持有 {@link PluginHook} 时使用。
     */
    public static ani.rss.plugin.api.DownloadListener.DownloadSnapshot downloadSnapshot(
            Ani ani, String hash, String name, String title, String subgroup, Double episode,
            String savePath, java.util.List<String> tags, Long size) {
        PluginHook current = hook();
        if (current != null) {
            return current.downloadSnapshot(ani, hash, name, title, subgroup, episode, savePath, tags, size);
        }
        return new ani.rss.plugin.api.DownloadListener.DownloadSnapshot(
                cn.hutool.core.util.StrUtil.nullToEmpty(hash),
                cn.hutool.core.util.StrUtil.nullToEmpty(name),
                cn.hutool.core.util.StrUtil.nullToEmpty(title),
                cn.hutool.core.util.StrUtil.blankToDefault(subgroup, "未知字幕组"),
                episode,
                cn.hutool.core.util.StrUtil.nullToEmpty(savePath),
                tags == null ? java.util.List.of() : tags,
                size,
                null);
    }

    public static boolean accepts(Ani ani, String title, String subgroup) {
        PluginHook current = hook();
        if (current == null) {
            return true;
        }
        try {
            return current.accepts(ani, title, subgroup);
        } catch (Exception e) {
            log.error("插件过滤执行失败");
            log.error(e.getMessage(), e);
            return true;
        }
    }
}
