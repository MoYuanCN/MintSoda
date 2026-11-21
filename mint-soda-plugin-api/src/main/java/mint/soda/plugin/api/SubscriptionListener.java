package mint.soda.plugin.api;

/**
 * 订阅生命周期监听，用于同步外部系统或统计。
 */
public interface SubscriptionListener {

    String id();

    default void onCreated(ItemFilter.SubscriptionSnapshot subscription) {
    }

    default void onUpdated(ItemFilter.SubscriptionSnapshot subscription) {
    }

    default void onDeleted(ItemFilter.SubscriptionSnapshot subscription) {
    }

    /**
     * 管理器需要按订阅 ID 定位时使用；默认遍历全部订阅。
     */
    default boolean accepts(String subscriptionId) {
        return true;
    }
}
