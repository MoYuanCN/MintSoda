package ani.rss.service.support;

import ani.rss.commons.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单轮刷新内的临时缓存。
 * <p>
 * 定时任务一次刷新会遍历所有订阅，同一目录、同一订阅的下载路径会被反复查询。
 * 这里把结果缓存在当前线程，轮次结束时释放，避免重复列目录与重复计算，降低机械硬盘唤醒次数。
 * <p>
 * 缓存不跨线程、不跨轮次，因此不需要加锁；需要共享状态的场景请使用普通服务缓存。
 */
public final class DownloadPass {

    private static final ThreadLocal<Cache> CURRENT = new ThreadLocal<>();

    private DownloadPass() {
    }

    /**
     * 一轮处理内的缓存内容
     */
    private static final class Cache {
        private final Map<String, List<File>> dirFiles = new HashMap<>();
        private final Map<String, String> downloadPaths = new HashMap<>();
    }

    /**
     * 开始一轮处理；重复调用会覆盖上一轮未正常结束的缓存。
     */
    public static void begin() {
        CURRENT.set(new Cache());
    }

    /**
     * 结束一轮处理，释放缓存。
     */
    public static void end() {
        CURRENT.remove();
    }

    /**
     * 是否处于某一轮处理中。
     */
    public static boolean active() {
        return CURRENT.get() != null;
    }

    /**
     * 列出目录内容，同一轮内相同目录只读取一次。
     *
     * @param path 目录
     * @return 文件列表
     */
    public static List<File> listFiles(String path) {
        Cache cache = CURRENT.get();
        if (cache == null) {
            return FileUtils.listFileList(path);
        }
        return cache.dirFiles.computeIfAbsent(path, FileUtils::listFileList);
    }

    /**
     * 删除文件后使目录缓存失效，避免同一轮内继续使用过期列表。
     *
     * @param path 目录
     */
    public static void invalidate(String path) {
        Cache cache = CURRENT.get();
        if (cache != null) {
            cache.dirFiles.remove(path);
        }
    }

    /**
     * 读取缓存的下载路径。
     *
     * @param key 缓存键
     * @return 缓存值，未命中返回 {@code null}
     */
    public static String getDownloadPath(String key) {
        Cache cache = CURRENT.get();
        return cache == null ? null : cache.downloadPaths.get(key);
    }

    /**
     * 写入下载路径缓存。
     *
     * @param key   缓存键
     * @param value 下载路径
     */
    public static void putDownloadPath(String key, String value) {
        Cache cache = CURRENT.get();
        if (cache != null) {
            cache.downloadPaths.put(key, value);
        }
    }
}
