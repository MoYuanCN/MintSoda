package mint.soda.plugin;

import mint.soda.plugin.api.PluginMessage;
import mint.soda.plugin.api.PluginMessageHandler;
import mint.soda.plugin.api.PluginMessageListener;
import mint.soda.plugin.api.PluginReply;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 插件之间的消息总线。
 * <p>
 * 主题是唯一寻址方式，插件之间不持有对方对象，停用或卸载插件只需清理订阅。
 * 每条消息在独立的虚拟线程里投递，慢订阅者或互相请求不会占满线程；
 * 请求式消息有固定超时，嵌套过深时直接忽略，避免两个插件互相应答形成死循环。
 */
@Slf4j
@Component
public class PluginBus {

    /**
     * 主题只允许小写字母、数字、点、下划线与连字符
     */
    public static final String TOPIC_REG = "^[a-z0-9][a-z0-9._-]{0,63}$";

    /**
     * 单条消息最大长度，避免插件之间传递大对象占满堆
     */
    public static final int MAX_PAYLOAD = 64 * 1024;

    /**
     * 请求式消息的等待上限
     */
    private static final long REQUEST_TIMEOUT_MS = 3_000L;

    /**
     * 请求嵌套上限，防止插件之间互相请求形成死循环
     */
    private static final int MAX_DEPTH = 4;

    private final Map<String, Map<String, PluginMessageListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Map<String, PluginMessageHandler>> handlers = new ConcurrentHashMap<>();

    /**
     * 每任务一个虚拟线程，慢订阅者不会占用固定线程，互相请求也不会耗尽线程池。
     */
    private final ExecutorService dispatcher = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 当前线程的请求层数，用于限制嵌套；请求处理线程会继承调用方的层数。
     */
    private final ThreadLocal<Integer> depth = new ThreadLocal<>();

    /**
     * 校验并规范化主题。
     *
     * @param topic 原始主题
     * @return 小写主题
     */
    public static String topic(String topic) {
        String normalized = StrUtil.trimToEmpty(topic).toLowerCase(java.util.Locale.ROOT);
        if (!ReUtil.isMatch(TOPIC_REG, normalized)) {
            throw new IllegalArgumentException("主题不合法，仅允许小写字母、数字、点、下划线与连字符");
        }
        return normalized;
    }

    /**
     * 校验载荷长度。
     *
     * @param payload 载荷
     * @return 空值转为空串后的载荷
     */
    public static String payload(String payload) {
        String value = StrUtil.nullToEmpty(payload);
        if (value.length() > MAX_PAYLOAD) {
            throw new IllegalArgumentException("消息内容超过 " + MAX_PAYLOAD + " 字符");
        }
        return value;
    }

    /**
     * 注册单向消息订阅；同一插件重复订阅同一主题时覆盖旧回调。
     *
     * @param pluginId 插件标识
     * @param topic    主题
     * @param listener 接收回调
     */
    public void subscribe(String pluginId, String topic, PluginMessageListener listener) {
        String key = topic(topic);
        if (listener == null) {
            removeFrom(listeners, key, pluginId);
            return;
        }
        listeners.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(pluginId, listener);
    }

    /**
     * 登记请求式消息的应答处理器；同一主题重复登记以最后一次为准。
     *
     * @param pluginId 插件标识
     * @param topic    主题
     * @param handler  应答处理器
     */
    public void serve(String pluginId, String topic, PluginMessageHandler handler) {
        String key = topic(topic);
        if (handler == null) {
            removeFrom(handlers, key, pluginId);
            return;
        }
        handlers.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(pluginId, handler);
    }

    /**
     * 取消某插件在某主题上的订阅与应答处理器。
     *
     * @param pluginId 插件标识
     * @param topic    主题
     */
    public void unsubscribe(String pluginId, String topic) {
        String key = topic(topic);
        removeFrom(listeners, key, pluginId);
        removeFrom(handlers, key, pluginId);
    }

    /**
     * 移除插件的全部订阅与处理器，停用或卸载时调用。
     *
     * @param pluginId 插件标识
     */
    public void removeAll(String pluginId) {
        listeners.values().forEach(map -> map.remove(pluginId));
        handlers.values().forEach(map -> map.remove(pluginId));
    }

    /**
     * 异步发布消息；没有订阅者时直接返回。
     *
     * @param source  发送方插件标识
     * @param topic   主题
     * @param payload 载荷
     */
    public void publish(String source, String topic, String payload) {
        String key = topic(topic);
        String body = payload(payload);
        Map<String, PluginMessageListener> snapshot = snapshot(listeners.get(key));
        // 广播只发给其他插件，避免插件收到自己发布的消息形成自激循环。
        snapshot.remove(source);
        if (snapshot.isEmpty()) {
            return;
        }
        PluginMessage message = new PluginMessage(key, source, body);
        for (Map.Entry<String, PluginMessageListener> entry : snapshot.entrySet()) {
            String pluginId = entry.getKey();
            PluginMessageListener listener = entry.getValue();
            dispatcher.execute(() -> {
                try {
                    listener.onMessage(message);
                } catch (Exception e) {
                    log.error("插件 {} 处理主题 {} 的消息失败", pluginId, key);
                    log.error(e.getMessage(), e);
                }
            });
        }
    }

    /**
     * 同步请求：并发询问应答处理器并等待，超时后返回已收到的应答。
     *
     * @param source  发送方插件标识
     * @param topic   主题
     * @param payload 载荷
     * @return 应答列表，按插件标识排序
     */
    public List<PluginReply> request(String source, String topic, String payload) {
        String key = topic(topic);
        String body = payload(payload);
        int current = depth.get() == null ? 0 : depth.get();
        if (current >= MAX_DEPTH) {
            log.warn("插件 {} 的联动请求超过 {} 层，已忽略主题 {}", source, MAX_DEPTH, key);
            return List.of();
        }
        Map<String, PluginMessageHandler> snapshot = snapshot(handlers.get(key));
        // 请求不发给自身，避免插件自己应答自己。
        snapshot.remove(source);
        if (snapshot.isEmpty()) {
            return List.of();
        }
        PluginMessage message = new PluginMessage(key, source, body);
        int nextDepth = current + 1;
        List<String> ids = new ArrayList<>(snapshot.keySet());
        List<CompletableFuture<PluginReply>> futures = new ArrayList<>();
        for (String pluginId : ids) {
            PluginMessageHandler handler = snapshot.get(pluginId);
            futures.add(CompletableFuture.supplyAsync(() -> {
                // 处理线程继承层数，嵌套请求才能在更深一层被拦住。
                depth.set(nextDepth);
                try {
                    String reply = handler.handle(message);
                    return StrUtil.isBlank(reply) ? null : new PluginReply(pluginId, reply);
                } finally {
                    depth.remove();
                }
            }, dispatcher));
        }
        List<PluginReply> replies = new ArrayList<>();
        for (CompletableFuture<PluginReply> future : futures) {
            try {
                PluginReply reply = future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (reply != null) {
                    replies.add(reply);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (TimeoutException e) {
                log.warn("插件主题 {} 的请求超时，已返回部分应答", key);
                future.cancel(true);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                log.warn("插件消息处理失败 {}", cause.getMessage());
            }
        }
        replies.sort(Comparator.comparing(PluginReply::plugin));
        return List.copyOf(replies);
    }

    private static <T> Map<String, T> snapshot(Map<String, T> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static void removeFrom(Map<String, ? extends Map<String, ?>> maps, String topic, String pluginId) {
        Map<String, ?> map = maps.get(topic);
        if (map == null) {
            return;
        }
        map.remove(pluginId);
        if (map.isEmpty()) {
            maps.remove(topic);
        }
    }

    /**
     * 关闭分发线程，供应用退出时调用。
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        dispatcher.shutdownNow();
    }
}
