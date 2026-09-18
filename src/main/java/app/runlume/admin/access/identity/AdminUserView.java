package app.runlume.admin.access.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 本地账号的只读视图，不包含口令摘要。
 *
 * @param id 账号标识
 * @param email 登录邮箱
 * @param displayName 展示名称
 * @param status 账号状态
 * @param roles 本地角色码
 * @param permissions 权限码原始集合
 * @param createdAt 创建时间
 * @param updatedAt 最近更新时间
 * @param lastLoginAt 最近登录时间，可为空
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:50
 */
public record AdminUserView(
        UUID id,
        String email,
        String displayName,
        UserStatus status,
        Set<String> roles,
        Set<String> permissions,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt
) {

    /**
     * 固化必填字段。
     */
    public AdminUserView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
}
