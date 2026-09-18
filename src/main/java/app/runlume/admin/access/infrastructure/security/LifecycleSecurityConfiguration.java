package app.runlume.admin.access.infrastructure.security;

import app.runlume.admin.access.PlatformIntegrationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * 平台生命周期入站的服务身份边界。
 *
 * <p>平台使用模块 Client 取得 {@code aud=lifecycleAudience} 的 client_credentials Token，
 * 每次调用只授予当前端点的最小 Scope。本边界固定 Issuer、JWKS 与 Audience，
 * 并按 Scope 逐端点授权；请求 Body 中的 Account、实例和角色声明一律不可信。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:20
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
public class LifecycleSecurityConfiguration {

    /**
     * 生命周期端点的无状态服务身份安全链。
     *
     * @param http Spring Security 构建器
     * @param properties 平台接入配置
     * @return 安全过滤链
     * @throws Exception 构建失败
     */
    @Bean
    @Order(1)
    SecurityFilterChain lifecycleSecurityFilterChain(
            HttpSecurity http,
            PlatformIntegrationProperties properties
    ) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.serviceJwksUri().toString())
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.serviceIssuer()),
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD,
                        audience -> audience != null
                                && audience.contains(properties.lifecycleAudience())
                )
        );
        decoder.setJwtValidator(validator);
        http.securityMatcher("/integration/v1/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/integration/v1/app-instances"
                        ).hasAuthority("SCOPE_instance:provision")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/integration/v1/operations/*"
                        ).hasAuthority("SCOPE_instance:operation:read")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/integration/v1/app-instances/*/suspend"
                        ).hasAuthority("SCOPE_instance:suspend")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/integration/v1/app-instances/*/resume"
                        ).hasAuthority("SCOPE_instance:resume")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/integration/v1/app-instances/*"
                        ).hasAuthority("SCOPE_instance:deprovision")
                        .anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(decoder)));
        return http.build();
    }
}
