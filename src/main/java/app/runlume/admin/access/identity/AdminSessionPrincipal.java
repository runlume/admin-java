package app.runlume.admin.access.identity;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 可序列化的会话主体，业务域可见的最小身份事实。
 *
 * <p>本类型只保存派生身份与绝对过期时间，绝不保存平台 Token、Launch Code、Client Secret
 * 或完整 Claims。主体名称固定为登录邮箱，供 Spring Session 建立会话索引和按用户撤销会话。</p>
 *
 * @param userId 本地账号标识
 * @param platformUserId 平台用户标识；本地自有账号为空
 * @param workspaceId 会话所属本地工作区；平台会话由实例映射，本地账号可绑定自有工作区
 * @param platformAccountId 平台 Account 标识；本地自有账号为空
 * @param platformAppInstanceId 平台 AppInstance 标识；本地自有账号为空
 * @param email 登录邮箱
 * @param displayName 展示名称
 * @param roles 本地角色码
 * @param permissions 权限码原始集合；`*` 表示全部，`模块:*` 表示模块内全部
 * @param membershipRevision 平台成员授权修订号；本地自有账号为 0
 * @param expiresAt 会话绝对过期时间
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:50
 */
public record AdminSessionPrincipal(
        UUID userId,
        UUID platformUserId,
        UUID workspaceId,
        UUID platformAccountId,
        UUID platformAppInstanceId,
        String email,
        String displayName,
        Set<String> roles,
        Set<String> permissions,
        long membershipRevision,
        Instant expiresAt
) implements Serializable, Principal {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 固化身份字段，并区分三种会话形态：
     * 平台会话（平台用户、工作区与平台边界齐全）、本地工作区会话（仅工作区）、
     * 以及本地无工作区会话（自举与排障，平台字段与工作区均为空）。
     */
    public AdminSessionPrincipal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (membershipRevision < 0) {
            throw new IllegalArgumentException("Membership revision must not be negative.");
        }
        boolean platformSession = platformUserId != null
                && platformAccountId != null
                && platformAppInstanceId != null
                && workspaceId != null;
        boolean localWorkspaceSession = platformUserId == null
                && platformAccountId == null
                && platformAppInstanceId == null;
        if (!platformSession && !localWorkspaceSession) {
            throw new IllegalArgumentException(
                    "Platform user, account and app instance must be present together with a workspace."
            );
        }
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    /**
     * 判断会话是否已超过绝对过期时间。
     *
     * @param now 当前时间
     * @return 已过期时为 true
     */
    public boolean expiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    @Override
    public String getName() {
        return email;
    }
}
