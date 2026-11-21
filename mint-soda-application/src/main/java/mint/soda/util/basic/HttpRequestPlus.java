package mint.soda.util.basic;

import mint.soda.commons.ExceptionUtils;
import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
public class HttpRequestPlus extends HttpRequest {
    public HttpRequestPlus(UrlBuilder url) {
        super(url);
    }

    public static HttpRequest of(UrlBuilder url) {
        return new HttpRequestPlus(url);
    }

    public static HttpRequest of(String url) {
        // 去除分隔符重复
        url = url.replaceAll("(?<!https?:?)//", "/");
        return HttpRequestPlus.of(UrlBuilder.ofHttp(url, StandardCharsets.UTF_8));
    }

    public static HttpRequest of(String url, Charset charset) {
        // 去除分隔符重复
        url = url.replaceAll("(?<!https?:?)//", "/");
        return HttpRequestPlus.of(UrlBuilder.ofHttp(url, charset));
    }

    public static HttpRequest get(String url) {
        return HttpRequestPlus.of(url).method(Method.GET);
    }

    public static HttpRequest post(String url) {
        return HttpRequestPlus.of(url).method(Method.POST);
    }

    @Override
    public HttpResponse execute(boolean isAsync) {
        String url = getUrl();
        boolean trace = Boolean.TRUE.equals(mint.soda.util.other.ConfigUtil.CONFIG.getDevHttpTrace());
        long start = System.currentTimeMillis();
        try {
            HttpResponse response = super.execute(isAsync);
            if (trace) {
                // 只记录方法、状态与耗时，不记录请求头或请求体，避免泄露凭据。
                log.info("http {} {} {} {}ms", getMethod(), url, response.getStatus(),
                        System.currentTimeMillis() - start);
            }
            return response;
        } catch (Exception e) {
            String message = ExceptionUtils.getMessage(e);
            log.error("url: {}, error: {}", url, message);
            throw e;
        }
    }
}
