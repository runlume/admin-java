package app.runlume.admin.access.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 未启用平台接入时，生命周期入口整体拒绝。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:20
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "admin.platform.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class LifecycleDisabledSecurityConfiguration {

    /**
     * 拒绝全部生命周期请求。
     *
     * @param http Spring Security 构建器
     * @return 安全过滤链
     * @throws Exception 构建失败
     */
    @Bean
    @Order(1)
    SecurityFilterChain lifecycleDisabledSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/integration/v1/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll());
        return http.build();
    }
}
