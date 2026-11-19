package ani.rss.util.other;

import ani.rss.entity.Config;
import ani.rss.util.basic.HttpReq;
import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RSS 响应获取与缓存。
 * <p>
 * 只负责“拿到 XML”这件事：本轮缓存、条件请求、失败退避与上轮内容兜底。
 * 解析、过滤与订阅判断留在 {@link ItemsUtil}，两者改动原因不同，拆开后可以独立调整。
 * <p>
 * 状态是静态的，因为整轮刷新是单线程驱动的批处理；并发预览通过并发容器安全读写。
 */
@Slf4j
public final class RssFetcher {

    private static final Config CONFIG = ConfigUtil.CONFIG;

    /**
     * RSS 地址到最近一次响应体的缓存，用于同一轮任务中复用相同地址的结果。
     */
    private static final Map<String, Fetch> CACHE = new ConcurrentHashMap<>();

    /**
     * 同一轮任务内复用响应；任务结束后缓存仅作为极短时间的去重，避免预览反复拉取。
     */
    private static volatile boolean batchActive = false;

    /**
     * 非任务状态下的缓存有效期，避免预览界面展示过期内容。
     */
    private static final long PASSIVE_CACHE_TTL = 5_000L;

    /**
     * 各地址最近一次成功的响应，用于条件请求与请求失败时的兜底。
     */
    private static final Map<String, Fetch> LAST_GOOD = new ConcurrentHashMap<>();

    /**
     * 请求失败后的退避截止时间，避免短间隔轮询持续压垮异常站点。
     */
    private static final Map<String, Long> BACKOFF_UNTIL = new ConcurrentHashMap<>();

    /**
     * 记录已提示过失败退避的地址，避免高频轮询时刷屏。
     */
    private static final Set<String> BACKOFF_LOGGED = new ConcurrentHashSet<>();

    /**
     * 本轮已确认失败的地址（没有可用旧内容时），避免同一轮内反复重试。
     */
    private static final Set<String> FAILED_THIS_PASS = new ConcurrentHashSet<>();

    /**
     * 条件请求与退避记录的上限，超出时整体清理，避免长期运行后无界增长。
     */
    private static final int MAX_TRACKED_URLS = 4096;

    private RssFetcher() {
    }

    /**
     * 一次响应：正文、ETag 与获取时间。
     */
    private record Fetch(String xml, String etag, long fetchedAt) {
    }

    /**
     * 开始一轮任务，清理上一轮的响应缓存。
     */
    public static void beginBatch() {
        CACHE.clear();
        FAILED_THIS_PASS.clear();
        // 订阅被删除后 ETag 与退避记录没有入口清理，超过阈值时整体丢弃即可重建。
        if (LAST_GOOD.size() > MAX_TRACKED_URLS) {
            LAST_GOOD.clear();
        }
        if (BACKOFF_UNTIL.size() > MAX_TRACKED_URLS) {
            BACKOFF_UNTIL.clear();
            BACKOFF_LOGGED.clear();
        }
        batchActive = true;
    }

    /**
     * 结束一轮任务。
     */
    public static void endBatch() {
        CACHE.clear();
        FAILED_THIS_PASS.clear();
        batchActive = false;
    }

    /**
     * 本轮缓存中是否已有该地址，供预取去重。
     *
     * @param url RSS 地址
     * @return 是否已缓存
     */
    public static boolean cached(String url) {
        return CACHE.containsKey(url);
    }

    /**
     * 获取 RSS 内容，优先命中本轮缓存。
     *
     * @param url RSS 链接
     * @return XML
     */
    public static String get(String url) {
        Fetch cached = CACHE.get(url);
        if (cached != null && (batchActive
                || System.currentTimeMillis() - cached.fetchedAt() < PASSIVE_CACHE_TTL)) {
            return cached.xml();
        }
        String xml = fetch(url);
        CACHE.put(url, new Fetch(xml, null, System.currentTimeMillis()));
        return xml;
    }

    /**
     * 请求 RSS，支持条件请求与失败兜底。
     * <p>
     * 命中 ETag 时服务端返回 304，直接复用上次内容；请求异常时若存在上次成功的响应，
     * 会在退避期内继续使用旧内容，避免短间隔轮询反复失败并中断下载流程。
     */
    private static String fetch(String url) {
        long now = System.currentTimeMillis();
        Long backoffUntil = BACKOFF_UNTIL.get(url);
        Fetch lastGood = LAST_GOOD.get(url);
        if (backoffUntil != null && backoffUntil > now) {
            if (lastGood != null) {
                return lastGood.xml();
            }
            throw new IllegalStateException(StrFormatter.format("RSS 请求处于退避期 {}", url));
        }

        if (batchActive && FAILED_THIS_PASS.contains(url)) {
            throw new IllegalStateException(StrFormatter.format("RSS 本轮已失败，跳过 {}", url));
        }

        int timeout = Math.max(1, ObjectUtil.defaultIfNull(CONFIG.getRssTimeout(), 20)) * 1000;
        try {
            HttpRequest request = HttpReq.get(url).timeout(timeout);
            if (lastGood != null && StrUtil.isNotBlank(lastGood.etag())) {
                request.header("If-None-Match", lastGood.etag());
            }
            String xml = request.thenFunction(res -> {
                if (res.getStatus() == 304 && lastGood != null) {
                    return lastGood.xml();
                }
                HttpReq.assertStatus(res);
                HttpReq.assertXml(res);
                String body = res.body();
                Assert.notBlank(body, "xml is blank");
                Assert.isTrue(StrUtil.startWith(StrUtil.trim(body), '<'), "xml error");
                LAST_GOOD.put(url, new Fetch(body, res.header(Header.ETAG), System.currentTimeMillis()));
                return body;
            });

            Assert.notBlank(xml, "xml is blank");
            Assert.isTrue(StrUtil.startWith(StrUtil.trim(xml), '<'), "xml error");
            BACKOFF_UNTIL.remove(url);
            BACKOFF_LOGGED.remove(url);
            FAILED_THIS_PASS.remove(url);
            return xml;
        } catch (Exception e) {
            if (lastGood != null) {
                long backoff = Math.max(30_000L, Math.min(300_000L,
                        Math.max(1, ObjectUtil.defaultIfNull(CONFIG.getRssSleepSeconds(), 900)) * 3_000L));
                BACKOFF_UNTIL.put(url, System.currentTimeMillis() + backoff);
                if (BACKOFF_LOGGED.add(url)) {
                    log.warn("RSS 请求失败，暂时使用上次内容 {} {}", url, e.getMessage());
                }
                return lastGood.xml();
            }
            if (batchActive) {
                FAILED_THIS_PASS.add(url);
            }
            throw e;
        }
    }
}
