package app.runlume.admin.access.identity.infrastructure.security;

import app.runlume.admin.access.PlatformIntegrationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.time.Clock;

/**
 * 浏览器入口的失败关闭安全边界。
 *
 * <p>会话是服务端 Session + 不透明 Cookie，Token 不进入浏览器存储；密码登录使用
 * BCrypt 与 Spring Security 的口令认证，登录成功后只把派生的会话主体放进会话。</p>
 *
 * <p>CSRF 使用 Cookie + 请求头双提交：前端先取 {@code GET /api/v1/csrf}，随后在写请求
 * 携带 {@code X-XSRF-TOKEN}。Launch 入口由平台以跨站表单提交，按契约豁免 CSRF，其安全性
 * 来自 256 位一次性 Code、60 秒有效期与模块绑定。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:20
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class AdminSecurityConfiguration {

    /**
     * 提供可注入的判定时间源，便于测试固定时间。
     *
     * @return UTC 时钟
     */
    @Bean
    Clock securityClock() {
        return Clock.systemUTC();
    }

    /**
     * 口令编码器。
     *
     * @return BCrypt 编码器
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 本地账号的口令认证入口。
     *
     * @param detailsService 账号细节服务
     * @param passwordEncoder 口令编码器
     * @return 认证管理器
     */
    @Bean
    AuthenticationManager authenticationManager(
            AdminUserDetailsService detailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(detailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * 会话上下文仓库，直接绑定服务端 Session。
     *
     * @return 会话上下文仓库
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * 浏览器与业务 API 的安全链。
     *
     * @param http Spring Security 构建器
     * @param securityContextRepository 会话上下文仓库
     * @param properties 平台接入配置
     * @param clock 判定时间源
     * @return 安全过滤链
     * @throws Exception 构建失败
     */
    @Bean
    @Order(2)
    SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            PlatformIntegrationProperties properties,
            Clock clock
    ) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/platform-connection").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/register"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, properties.launchPath()).permitAll()
                        .requestMatchers("/integration/v1/**").denyAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.pathPattern(
                                        HttpMethod.POST,
                                        properties.launchPath()
                                )
                        ))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern("/api/**")
                ))
                .addFilterAfter(
                        new SessionAbsoluteExpiryFilter(clock),
                        SecurityContextHolderFilter.class
                )
                ;
        return http.build();
    }
}
