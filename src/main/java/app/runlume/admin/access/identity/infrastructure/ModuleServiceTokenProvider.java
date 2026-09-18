package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.PlatformIntegrationProperties;
import app.runlume.admin.access.identity.IdentityProblem;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块服务身份的 client_credentials Token 提供者。
 *
 * <p>SDK 按设计不申请、不缓存任何凭据，因此由本系统持有唯一的模块服务身份，并按 Scope
 * 在内存中缓存到过期前 30 秒。Token 与 Client Secret 不写日志、不落库、不进入会话。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:30
 */
@Component
@ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
public class ModuleServiceTokenProvider {

    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);
    private static final long DEFAULT_LIFETIME_SECONDS = 300L;

    private final PlatformIntegrationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    /**
     * 创建模块服务 Token 提供者。
     *
     * @param properties 平台接入配置
     * @param objectMapper JSON 解码器
     */
    public ModuleServiceTokenProvider(
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
     * 取得指定 Scope 的模块服务 Token，必要时刷新。
     *
     * @param scope 最小必需 Scope
     * @return 不含 {@code Bearer } 前缀的访问令牌
     * @throws IdentityProblem 令牌端点在超时内未返回可用令牌
     */
    public String tokenFor(String scope) {
        CachedToken current = tokens.get(scope);
        Instant now = Instant.now();
        if (current != null && now.isBefore(current.expiresAt())) {
            return current.value();
        }
        synchronized (tokens) {
            CachedToken rechecked = tokens.get(scope);
            Instant currentTime = Instant.now();
            if (rechecked != null && currentTime.isBefore(rechecked.expiresAt())) {
                return rechecked.value();
            }
            CachedToken refreshed = request(scope, currentTime);
            tokens.put(scope, refreshed);
            return refreshed.value();
        }
    }

    private CachedToken request(String scope, Instant now) {
        StringBuilder form = new StringBuilder()
                .append("grant_type=client_credentials")
                .append("&client_id=").append(encode(properties.serviceClientId()))
                .append("&client_secret=").append(encode(properties.serviceClientSecret()));
        properties.serviceAudienceOrEmpty().ifPresent(audience -> form
                .append("&audience=").append(encode(audience)));
        form.append("&scope=").append(encode(scope));
        try {
            HttpRequest request = HttpRequest.newBuilder(properties.serviceTokenUri())
                    .timeout(properties.requestTimeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (response.statusCode() != 200 || response.body() == null) {
                throw unavailable();
            }
            TokenResponse payload = objectMapper.readValue(
                    response.body(),
                    TokenResponse.class
            );
            if (payload.accessToken() == null || payload.accessToken().isBlank()) {
                throw unavailable();
            }
            long lifetime = payload.expiresIn() == null
                    ? DEFAULT_LIFETIME_SECONDS
                    : payload.expiresIn();
            long usable = Math.max(lifetime - EXPIRY_MARGIN.toSeconds(), 1L);
            return new CachedToken(payload.accessToken(), now.plusSeconds(usable));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | RuntimeException exception) {
            throw unavailable();
        }
    }

    private static IdentityProblem unavailable() {
        return IdentityProblem.of(IdentityProblem.Code.PLATFORM_UNAVAILABLE);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * 令牌端点响应。
     *
     * @param accessToken 访问令牌
     * @param expiresIn 有效期秒数
     */
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
