package app.runlume.admin.access.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 本地账号与会话配置。
 *
 * @param registrationEnabled 是否开放自助注册
 * @param sessionTtl 本地会话绝对有效期
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:40
 */
@ConfigurationProperties(prefix = "admin.local")
public record LocalAccountProperties(boolean registrationEnabled, Duration sessionTtl) {

    /**
     * 补齐会话有效期默认值。
     */
    public LocalAccountProperties {
        sessionTtl = sessionTtl == null ? Duration.ofHours(12) : sessionTtl;
    }
}
