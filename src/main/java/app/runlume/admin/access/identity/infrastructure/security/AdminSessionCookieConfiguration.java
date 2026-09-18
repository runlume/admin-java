package app.runlume.admin.access.identity.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.util.Locale;

/**
 * 显式声明 Spring Session 的 Cookie 序列化。
 *
 * <p>Spring Session 的 {@code JdbcHttpSessionConfiguration} 会先注册
 * {@code CookieHttpSessionIdResolver}，使 Boot 的会话自动配置判定“组件已存在”而不再构建
 * {@code CookieSerializer}，最终落到 Spring Session 的默认 Cookie 名。本配置直接提供
 * {@code CookieSerializer}，保证 {@code server.servlet.session.cookie.*} 真正生效。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 12:10
 */
@Configuration(proxyBeanMethods = false)
public class AdminSessionCookieConfiguration {

    /**
     * 由 {@code server.servlet.session.cookie.*} 驱动的会话 Cookie 序列化。
     *
     * @param cookieName Cookie 名称
     * @param secure 是否只允许安全传输
     * @param httpOnly 是否禁止脚本读取
     * @param sameSite SameSite 属性
     * @param path Cookie 路径
     * @return Cookie 序列化
     */
    @Bean
    CookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.name:ADMIN_SESSION}") String cookieName,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secure,
            @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite,
            @Value("${server.servlet.session.cookie.path:/}") String path
    ) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setUseSecureCookie(secure);
        serializer.setUseHttpOnlyCookie(httpOnly);
        serializer.setSameSite(normalizeSameSite(sameSite));
        serializer.setCookiePath(path);
        return serializer;
    }

    private static String normalizeSameSite(String sameSite) {
        if (sameSite == null || sameSite.isBlank()) {
            return "Lax";
        }
        return sameSite.substring(0, 1).toUpperCase(Locale.ROOT)
                + sameSite.substring(1).toLowerCase(Locale.ROOT);
    }
}
