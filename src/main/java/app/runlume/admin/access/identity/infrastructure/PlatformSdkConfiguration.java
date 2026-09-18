package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.PlatformIntegrationProperties;
import app.runlume.platform.sdk.identity.InstanceServiceTokens;
import app.runlume.platform.sdk.identity.PlatformConnectionProbe;
import app.runlume.platform.sdk.identity.PlatformLaunchClient;
import app.runlume.platform.sdk.identity.PlatformRuntimeClientFactory;
import app.runlume.platform.sdk.identity.PlatformServiceTokens;
import app.runlume.platform.sdk.identity.RuntimeIdentityClient;
import app.runlume.platform.sdk.identity.SessionValidationClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

/**
 * 装配平台集成 SDK 的通用辅助组件。
 *
 * <p>这些组件只做协议与凭据处理，不持有本地状态、不落盘：模块服务令牌、实例服务令牌与运行时
 * 身份客户端由 SDK 提供，本系统只注入部署配置。联机探针无条件是 Bean，登录说明页在平台关闭时
 * 也需要一个失败关闭的结论。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 18:00
 */
@Configuration(proxyBeanMethods = false)
public class PlatformSdkConfiguration {

    /**
     * 平台运行时联机探针。
     *
     * @param properties 平台接入配置
     * @param clock      判定时间源
     * @return 只探测部署配置公钥地址的探针
     */
    @Bean
    PlatformConnectionProbe platformConnectionProbe(
            PlatformIntegrationProperties properties,
            Clock clock
    ) {
        return new PlatformConnectionProbe(
                properties.runtimeJwksUri(),
                Duration.ofSeconds(10),
                clock
        );
    }

    /**
     * 模块服务身份令牌提供者。
     *
     * @param properties 平台接入配置
     * @param clock      判定时间源
     * @return 按 Scope 缓存的 client_credentials 令牌提供者
     */
    @Bean
    @ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
    PlatformServiceTokens platformServiceTokens(
            PlatformIntegrationProperties properties,
            Clock clock
    ) {
        return new PlatformServiceTokens(
                new PlatformServiceTokens.Config(
                        properties.serviceTokenUri(),
                        properties.serviceClientId(),
                        properties.serviceClientSecret(),
                        properties.serviceAudienceOrEmpty().orElse(null),
                        properties.connectTimeout(),
                        properties.requestTimeout()
                ),
                clock
        );
    }

    /**
     * 运行时身份客户端。
     *
     * @param properties 平台接入配置
     * @param clock      判定时间源
     * @return 已装配验签器与超时的客户端
     */
    @Bean
    @ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
    RuntimeIdentityClient runtimeIdentityClient(
            PlatformIntegrationProperties properties,
            Clock clock
    ) {
        return PlatformRuntimeClientFactory.create(
                new PlatformRuntimeClientFactory.Config(
                        properties.baseUri(),
                        properties.moduleId(),
                        properties.runtimeIssuer(),
                        URI.create(properties.runtimeJwksUri().toString()),
                        properties.runtimeAudience(),
                        properties.connectTimeout(),
                        properties.requestTimeout()
                ),
                clock
        );
    }

    /**
     * 实例服务令牌缓存。
     *
     * @param identity      运行时身份客户端
     * @param serviceTokens 模块服务令牌提供者
     * @param clock         判定时间源
     * @return 按实例与 Scope 缓存的实例服务令牌
     */
    @Bean
    @ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
    InstanceServiceTokens instanceServiceTokens(
            RuntimeIdentityClient identity,
            PlatformServiceTokens serviceTokens,
            Clock clock
    ) {
        return new InstanceServiceTokens(identity, serviceTokens, clock);
    }

    /**
     * Launch 交换客户端。
     *
     * @param identity      运行时身份客户端
     * @param serviceTokens 模块服务令牌提供者
     * @return 自带模块服务令牌申请的 Launch 客户端
     */
    @Bean
    @ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
    PlatformLaunchClient platformLaunchClient(
            RuntimeIdentityClient identity,
            PlatformServiceTokens serviceTokens
    ) {
        return new PlatformLaunchClient(identity, serviceTokens);
    }

    /**
     * 会话校验客户端。
     *
     * @param identity       运行时身份客户端
     * @param instanceTokens 实例服务令牌缓存
     * @return 使用 {@code session:check} Scope 的会话校验客户端
     */
    @Bean
    @ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
    SessionValidationClient sessionValidationClient(
            RuntimeIdentityClient identity,
            InstanceServiceTokens instanceTokens
    ) {
        return new SessionValidationClient(identity, instanceTokens, "session:check");
    }
}
