package ani.rss.plugin.api;

import java.util.Map;

/**
 * 下载生命周期监听。
 * <p>
 * 与 {@link DownloadInterceptor} 不同，监听器不能修改任务，只能观察结果，
 * 适合统计、外部同步与自动化通知。所有回调都在宿主主流程之外执行，
 * 抛出异常只会记录日志，不影响下载。
 */
public interface DownloadListener {

    /**
     * 监听器标识
     */
    String id();

    /**
     * 任务成功提交给下载器后调用
     */
    default void onAdded(DownloadSnapshot task, Map<String, Object> context) {
    }

    /**
     * 下载器报告任务完成且已发送完成通知后调用
     */
    default void onCompleted(DownloadSnapshot task, Map<String, Object> context) {
    }

    /**
     * 任务提交失败时调用，失败后不会再重试
     */
    default void onFailed(DownloadSnapshot task, Map<String, Object> context) {
    }

    /**
     * 下载任务快照
     *
     * @param hash         种子哈希
     * @param name         重命名后的名称
     * @param title        原始标题
     * @param subgroup     字幕组
     * @param episode      集数
     * @param savePath     最终保存目录
     * @param tags         任务标签
     * @param size         体积（字节），未知为 {@code null}
     * @param subscription 所属订阅
     */
    record DownloadSnapshot(
            String hash,
            String name,
            String title,
            String subgroup,
            Double episode,
            String savePath,
            java.util.List<String> tags,
            Long size,
            ItemFilter.SubscriptionSnapshot subscription
    ) {
        public DownloadSnapshot {
            tags = tags == null ? java.util.List.of() : java.util.List.copyOf(tags);
        }
    }
}
