package app.runlume.admin.access;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * 平台接入的固定配置。
 *
 * <p>所有平台目标只取本配置，浏览器参数、请求体和自定义 Header 不能覆盖。Client Secret
 * 只能来自环境变量或密钥管理，禁止写入仓库。</p>
 *
 * @param enabled             是否启用平台接入；关闭时 Launch 与生命周期入口整体拒绝
 * @param moduleId            本系统在平台登记的稳定模块标识
 * @param baseUri             平台控制面地址，供 Launch 交换使用
 * @param runtimeIssuer       平台运行时 Token 的预期 Issuer
 * @param runtimeJwksUri      平台运行时公钥地址
 * @param runtimeAudience     平台签给本系统的运行时 Audience
 * @param launchPath          浏览器提交 Launch Code 的本站路径
 * @param serviceIssuer       平台生命周期服务 Token 的预期 Issuer
 * @param serviceJwksUri      平台服务身份公钥地址
 * @param serviceAudience     模块服务 Token 的 Audience
 * @param lifecycleAudience   平台调用本系统生命周期端点的 Audience
 * @param serviceTokenUri     模块服务身份的 client_credentials 令牌地址
 * @param serviceClientId     模块服务身份 Client ID
 * @param serviceClientSecret 模块服务身份 Client Secret
 * @param connectTimeout      出站连接超时
 * @param requestTimeout      出站请求超时
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:00
 */
@ConfigurationProperties(prefix = "admin.platform")
public record PlatformIntegrationProperties(
        boolean enabled,
        String moduleId,
        URI baseUri,
        String runtimeIssuer,
        URI runtimeJwksUri,
        String runtimeAudience,
        String launchPath,
        String serviceIssuer,
        URI serviceJwksUri,
        String serviceAudience,
        String lifecycleAudience,
        URI serviceTokenUri,
        String serviceClientId,
        String serviceClientSecret,
        Duration connectTimeout,
        Duration requestTimeout
) {

    /**
     * 规范化可选路径。
     */
    public PlatformIntegrationProperties {
        launchPath = launchPath == null || launchPath.isBlank() ? "/launch" : launchPath;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
    }

    /**
     * 返回配置的模块服务 Token 目标 Audience。
     *
     * @return Audience；未配置时为空
     */
    public Optional<String> serviceAudienceOrEmpty() {
        return Optional.ofNullable(serviceAudience);
    }
}
