package mint.soda.plugin.api;

import java.util.Map;

/**
 * 为所有模板追加自定义占位符。
 * <p>
 * 通知、重命名与下载路径模板都会先取宿主内置字段，再叠加插件提供的字段；
 * 与内置字段同名时插件值优先生效。
 */
public interface TemplateVariableProvider {

    /**
     * 提供方标识
     */
    String id();

    /**
     * 当前可用的字段；返回空 Map 表示本次不提供任何字段。
     *
     * @param ani 订阅快照
     * @return 字段名到值的映射，字段名不带 {@code ${}} 包裹
     */
    Map<String, String> variables(ItemFilter.SubscriptionSnapshot ani);
}
