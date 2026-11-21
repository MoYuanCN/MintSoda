package mint.soda.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内通用缓存。
 * <p>
 * 使用并发容器替代整表加锁：命中路径完全无锁，读取不再互相阻塞。
 * 容量固定，超出后按写入顺序淘汰；带过期时间的条目在读取或淘汰时清理。
 */
public final class CacheUtils {

    private static final int MAX_SIZE = 1024 * 8;
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Map<Object, Entry> CACHE = new ConcurrentHashMap<>();

    private CacheUtils() {
    }

    private record Entry(Object value, long expireAt, long sequence) {
        boolean expired(long now) {
            return expireAt > 0 && expireAt <= now;
        }
    }

    @SuppressWarnings("unchecked")
    public static <V> V get(Object key) {
        if (key == null) {
            return null;
        }
        Entry entry = CACHE.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expired(System.currentTimeMillis())) {
            CACHE.remove(key, entry);
            return null;
        }
        return (V) entry.value();
    }

    public static void put(Object key, Object value) {
        put(key, value, 0L);
    }

    public static void put(Object key, Object value, long timeout) {
        if (key == null || value == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long expireAt = timeout > 0 ? now + timeout : 0L;
        CACHE.put(key, new Entry(value, expireAt, SEQUENCE.incrementAndGet()));
        if (CACHE.size() > MAX_SIZE) {
            evict();
        }
    }

    public static boolean containsKey(Object key) {
        return get(key) != null;
    }

    public static void remove(Object key) {
        if (key == null) {
            return;
        }
        CACHE.remove(key);
    }

    /**
     * 淘汰：先清理已过期条目，仍超出容量时移除写入最早的条目。
     */
    private static void evict() {
        long now = System.currentTimeMillis();
        CACHE.entrySet().removeIf(entry -> entry.getValue().expired(now));
        while (CACHE.size() > MAX_SIZE) {
            Map.Entry<Object, Entry> oldest = null;
            for (Iterator<Map.Entry<Object, Entry>> it = CACHE.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Object, Entry> candidate = it.next();
                if (oldest == null || candidate.getValue().sequence() < oldest.getValue().sequence()) {
                    oldest = candidate;
                }
            }
            if (oldest == null) {
                return;
            }
            CACHE.remove(oldest.getKey(), oldest.getValue());
        }
    }
}
