package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.PlatformIntegrationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * 登录说明页使用的平台联机探针。
 *
 * <p>只探测固定的平台运行面 JWKS 入口：连接与请求超时各 {@code 5s}、禁止重定向、响应上限
 * {@code 64 KiB}，并且只有在 HTTP 200、JSON 合法且 {@code keys} 为非空数组时才判定联机。
 * 超时、网络错误、非 200、非法 JSON 与空密钥集一律失败关闭。</p>
 *
 * <p>本探针不发送任何模块凭据，不记录响应体、地址或异常，只回答“当前后端能否解析平台公钥”。
 * 它不证明浏览器已登录、用户已获实例授权，也不证明 Launch 与本地会话正常。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:30
 */
@Component
public class PlatformConnectionProbe {

    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final PlatformIntegrationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile CachedResult cached;

    /**
     * 创建联机探针。
     *
     * @param properties 平台接入配置
     * @param objectMapper JSON 解码器
     */
    public PlatformConnectionProbe(
            PlatformIntegrationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 返回平台联机状态，匿名请求共享十秒结果缓存。
     *
     * @return 平台公钥可解析时为 true
     */
    public boolean connected() {
        CachedResult current = cached;
        Instant now = Instant.now();
        if (current != null && now.isBefore(current.expiresAt())) {
            return current.connected();
        }
        boolean connected = probe();
        cached = new CachedResult(connected, Instant.now().plus(CACHE_TTL));
        return connected;
    }

    private boolean probe() {
        if (!properties.enabled() || properties.runtimeJwksUri() == null) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(properties.runtimeJwksUri())
                    .timeout(properties.requestTimeout())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            byte[] body = response.body();
            if (response.statusCode() != 200 || body == null || body.length == 0
                    || body.length > MAX_BODY_BYTES) {
                return false;
            }
            JsonNode keys = objectMapper.readTree(body).path("keys");
            return keys.isArray() && keys.size() > 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private record CachedResult(boolean connected, Instant expiresAt) {
    }
}
