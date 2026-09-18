package app.runlume.admin.access.identity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 已验证的 Launch 身份最小投影，不携带原始 Token 或 Launch Code。
 *
 * @param platformUserId 平台 User 标识
 * @param platformAccountId 平台 Account 标识
 * @param platformAppInstanceId 平台 AppInstance 标识
 * @param displayName 展示名称
 * @param email 邮箱，平台可能不提供
 * @param roles 实例内平台角色
 * @param membershipRevision 实例成员授权修订号
 * @param expiresAt Context Token 的绝对过期时间
 * @param actionPath 平台固化的可选站内目标路径
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:50
 */
public record PlatformLaunchIdentity(
        UUID platformUserId,
        UUID platformAccountId,
        UUID platformAppInstanceId,
        String displayName,
        String email,
        List<String> roles,
        long membershipRevision,
        Instant expiresAt,
        String actionPath
) {

    /**
     * 固化必填字段。
     */
    public PlatformLaunchIdentity {
        Objects.requireNonNull(platformUserId, "platformUserId");
        Objects.requireNonNull(platformAccountId, "platformAccountId");
        Objects.requireNonNull(platformAppInstanceId, "platformAppInstanceId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(expiresAt, "expiresAt");
        roles = List.copyOf(roles);
    }
}
