package ani.rss.plugin.api;

import java.util.List;
import java.util.Map;

/**
 * 下载拦截：在种子提交给下载器之前调整任务，或直接取消。
 * <p>
 * 与 {@link ItemFilter} 不同，拦截器拿到的是已经解析完毕、已确定保存位置的任务，
 * 适合做保存位置改写、命名调整与按体积/标签拦截。
 */
public interface DownloadInterceptor {

    /**
     * 拦截器标识
     */
    String id();

    /**
     * 优先级，数值越小越先执行
     */
    default int order() {
        return 100;
    }

    /**
     * 处理任务。
     * <p>
     * 可以返回新的 {@link DownloadCandidate} 修改 {@code savePath} 或 {@code name}，
     * 返回 {@code null} 表示取消本次下载；其余字段仅作为只读上下文。
     *
     * @param candidate 任务快照
     * @param context   额外上下文
     * @return 修改后的任务，或 {@code null} 取消
     */
    DownloadCandidate intercept(DownloadCandidate candidate, Map<String, Object> context);

    /**
     * 任务快照
     *
     * @param hash         种子哈希
     * @param name         重命名后的名称
     * @param title        原始标题
     * @param subgroup     字幕组
     * @param episode      集数
     * @param savePath     保存目录
     * @param tags         即将写入的标签
     * @param magnet       磁力链接
     * @param size         体积（字节），未知为 {@code null}
     * @param subscription 所属订阅
     */
    record DownloadCandidate(
            String hash,
            String name,
            String title,
            String subgroup,
            Double episode,
            String savePath,
            List<String> tags,
            String magnet,
            Long size,
            ItemFilter.SubscriptionSnapshot subscription
    ) {
        public DownloadCandidate {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }

        public DownloadCandidate withSavePath(String path) {
            return new DownloadCandidate(hash, name, title, subgroup, episode, path, tags, magnet, size, subscription);
        }

        public DownloadCandidate withName(String newName) {
            return new DownloadCandidate(hash, newName, title, subgroup, episode, savePath, tags, magnet, size, subscription);
        }
    }
}
