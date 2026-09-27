package com.link.dispatcher.util;

import io.netty.handler.codec.quic.EpollQuicUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 工具类：基于 JDK 内置 HttpClient（Java 11+），不引入第三方 HTTP 依赖。
 *
 * <p>HttpClient 实例是线程安全的，全局复用一个即可（内部维护连接池）。</p>
 */
public class HttpClientUtil {

    /** 建立连接超时 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 读取响应超时 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON_TYPE = "application/json;charset=UTF-8";
    private static final String FORM_TYPE = "application/x-www-form-urlencoded;charset=UTF-8";

    public static final String AI_URL = "http://127.0.0.1:8200/ai/ask";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpClientUtil() {
    }

    // ==================== GET ====================

    /**
     * 发送GET请求
     */
    public static String get(String url) {
        return get(url, null, null);
    }

    /**
     * 发送GET请求，params 会拼接到 URL 上（自动做 URL 编码）
     */
    public static String get(String url, Map<String, ?> params) {
        return get(url, params, null);
    }

    /**
     * 发送GET请求，可携带查询参数与请求头
     */
    public static String get(String url, Map<String, ?> params, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(buildUrl(url, params)))
                .timeout(READ_TIMEOUT)
                .GET();

        return execute(builder, headers);
    }

    // ==================== POST ====================

    public static String postJson(String json) {
        return postJson(AI_URL, json, null);
    }

    /**
     * 发送POST请求，提交JSON体
     */
    public static String postJson(String url, String json) {
        return postJson(url, json, null);
    }

    /**
     * 发送POST请求，提交JSON体，可携带请求头
     */
    public static String postJson(String url, String json, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header(CONTENT_TYPE, JSON_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(
                        json == null ? "" : json, StandardCharsets.UTF_8
                ));

        return execute(builder, headers);
    }

    /**
     * 发送POST请求，提交表单（application/x-www-form-urlencoded）
     */
    public static String postForm(String url, Map<String, ?> params) {
        return postForm(url, params, null);
    }

    /**
     * 发送POST请求，提交表单，可携带请求头
     */
    public static String postForm(String url, Map<String, ?> params, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header(CONTENT_TYPE, FORM_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(
                        encodeParams(params), StandardCharsets.UTF_8
                ));

        return execute(builder, headers);
    }

    // ==================== 内部方法 ====================

    /**
     * 执行请求，2xx 返回响应体，否则抛异常
     */
    private static String execute(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k != null && v != null) {
                    builder.header(k, v);
                }
            });
        }

        HttpRequest request = builder.build();

        try {
            HttpResponse<String> response = CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            int code = response.statusCode();

            if (code < 200 || code >= 300) {
                throw new RuntimeException(
                        "HTTP请求失败，状态码：" + code + "，响应：" + response.body()
                );
            }

            return response.body();

        } catch (IOException e) {
            throw new RuntimeException("HTTP请求异常：" + request.uri(), e);

        } catch (InterruptedException e) {
            // 恢复中断标记，避免吞掉中断信号
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP请求被中断：" + request.uri(), e);
        }
    }

    /**
     * 把查询参数拼到 URL 上，已有 ? 时用 & 追加
     */
    private static String buildUrl(String url, Map<String, ?> params) {
        String query = encodeParams(params);

        if (query.isEmpty()) {
            return url;
        }

        return url + (url.contains("?") ? "&" : "?") + query;
    }

    /**
     * 参数编码成 k1=v1&k2=v2，值为 null 的参数跳过
     */
    private static String encodeParams(Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, ?> entry : params.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            if (sb.length() > 0) {
                sb.append("&");
            }

            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }

        return sb.toString();
    }

}
