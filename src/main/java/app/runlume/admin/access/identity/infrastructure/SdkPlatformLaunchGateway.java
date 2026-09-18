package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.PlatformIntegrationProperties;
import app.runlume.admin.access.identity.IdentityProblem;
import app.runlume.admin.access.identity.PlatformLaunchGateway;
import app.runlume.admin.access.identity.PlatformLaunchIdentity;
import app.runlume.platform.sdk.identity.ApiClient;
import app.runlume.platform.sdk.identity.RuntimeIdentityClient;
import app.runlume.platform.sdk.security.PlatformJwtVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * 使用平台集成 SDK 交换一次性 Launch Code。
 *
 * <p>SDK 负责调用平台、验证 {@code iss/aud/kid/签名/exp} 与模块边界，并把结果收敛为不含
 * 原始 Token 的最小投影。本适配器只做凭据注入、稳定失败分类与配置绑定，不复制平台协议。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:30
 */
@Component
@ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
public class SdkPlatformLaunchGateway implements PlatformLaunchGateway {

    private static final String LAUNCH_SCOPE = "launch:exchange";
    private static final String INSTANCE_TOKEN_AUDIENCE = "platform-runtime-api";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final RuntimeIdentityClient client;
    private final ModuleServiceTokenProvider tokenProvider;

    /**
     * 创建 SDK Launch 适配器。
     *
     * @param properties 平台接入配置
     * @param tokenProvider 模块服务 Token 提供者
     * @param clock 判定时间源
     */
    public SdkPlatformLaunchGateway(
            PlatformIntegrationProperties properties,
            ModuleServiceTokenProvider tokenProvider,
            Clock clock
    ) {
        this.tokenProvider = tokenProvider;
        ApiClient apiClient = new ApiClient(
                HttpClient.newBuilder()
                        .connectTimeout(properties.connectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER),
                ApiClient.createDefaultObjectMapper(),
                properties.baseUri().toString()
        );
        apiClient.setConnectTimeout(properties.connectTimeout());
        apiClient.setReadTimeout(properties.requestTimeout());
        this.client = new RuntimeIdentityClient(
                apiClient,
                properties.moduleId(),
                verifier(properties, properties.runtimeAudience(), clock),
                verifier(properties, INSTANCE_TOKEN_AUDIENCE, clock)
        );
    }

    @Override
    public PlatformLaunchIdentity exchange(String launchCode) {
        String moduleToken;
        try {
            moduleToken = tokenProvider.tokenFor(LAUNCH_SCOPE);
        } catch (IdentityProblem problem) {
            throw IdentityProblem.of(IdentityProblem.Code.LAUNCH_UNAVAILABLE);
        }
        try {
            RuntimeIdentityClient.VerifiedLaunch verified = client.exchangeLaunchGrant(
                    moduleToken,
                    launchCode
            );
            return new PlatformLaunchIdentity(
                    verified.platformUserId(),
                    verified.platformAccountId(),
                    verified.platformAppInstanceId(),
                    verified.displayName(),
                    verified.email(),
                    verified.roles(),
                    verified.expiresAt(),
                    verified.actionPath()
            );
        } catch (RuntimeIdentityClient.LaunchException exception) {
            throw switch (exception.reason()) {
                case INVALID, USED, EXPIRED, MODULE_MISMATCH ->
                        IdentityProblem.of(IdentityProblem.Code.LAUNCH_REJECTED);
                case UNAVAILABLE ->
                        IdentityProblem.of(IdentityProblem.Code.LAUNCH_UNAVAILABLE);
            };
        } catch (RuntimeException exception) {
            throw IdentityProblem.of(IdentityProblem.Code.LAUNCH_UNAVAILABLE);
        }
    }

    private static PlatformJwtVerifier verifier(
            PlatformIntegrationProperties properties,
            String audience,
            Clock clock
    ) {
        return new PlatformJwtVerifier(
                properties.runtimeIssuer(),
                audience,
                PlatformJwtVerifier.httpJwksSource(properties.runtimeJwksUri()),
                clock,
                CLOCK_SKEW
        );
    }
}
