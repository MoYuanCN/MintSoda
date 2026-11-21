package mint.soda.plugin.api;

/**
 * 插件对外暴露的 HTTP 接口。
 * <p>
 * 宿主以 {@code /api/plugin/api/{pluginId}/{path}} 分发，所有请求都要求登录，
 * 与插件页同一鉴权级别。插件自行解析 body 并返回 JSON 字符串。
 */
public interface PluginEndpoint {

    /**
     * 相对路径，例如 {@code status} 对应 {@code /api/plugin/api/{pluginId}/status}
     */
    String path();

    /**
     * 允许的方法：GET、POST、PUT、DELETE；为空表示不限制
     */
    default java.util.Set<String> methods() {
        return java.util.Set.of("POST");
    }

    /**
     * 处理请求
     *
     * @param request 请求内容
     * @return JSON 字符串，可为空
     */
    String handle(EndpointRequest request) throws Exception;

    /**
     * 请求快照
     *
     * @param method      请求方法
     * @param path        相对路径
     * @param query       查询参数
     * @param body        请求体
     * @param contentType 内容类型
     */
    record EndpointRequest(
            String method,
            String path,
            java.util.Map<String, String> query,
            String body,
            String contentType
    ) {
        public EndpointRequest {
            query = query == null ? java.util.Map.of() : java.util.Map.copyOf(query);
        }
    }
}
