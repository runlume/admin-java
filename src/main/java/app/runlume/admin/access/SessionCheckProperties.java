package app.runlume.admin.access;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 会话校验的缓存窗口配置。
 *
 * <p>撤销窗口等于这里声明的窗口：读请求允许更长的缓存，写请求收紧到更短窗口。</p>
 *
 * @param readWindow  读请求的成员校验缓存窗口
 * @param writeWindow 写请求的成员校验缓存窗口
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:35
 */
@ConfigurationProperties(prefix = "admin.platform.session-check")
public record SessionCheckProperties(Duration readWindow, Duration writeWindow) {

    /**
     * 补默认窗口，并要求窗口为正。
     */
    public SessionCheckProperties {
        readWindow = readWindow == null ? Duration.ofSeconds(60) : readWindow;
        writeWindow = writeWindow == null ? Duration.ofSeconds(30) : writeWindow;
        if (readWindow.isNegative() || writeWindow.isNegative()) {
            throw new IllegalArgumentException("Session check windows must not be negative.");
        }
    }
}
