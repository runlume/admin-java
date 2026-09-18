package app.runlume.admin.access.identity;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 平台侧校验得出的成员会话状态。
 *
 * @param platformUserId     平台用户标识
 * @param active             实例、实例成员、Account 与 Account 成员同时有效
 * @param roles              实例作用域角色；不活跃时为空
 * @param membershipRevision 成员授权修订号；不活跃时为 {@code 0}
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:35
 */
public record SessionState(
        UUID platformUserId,
        boolean active,
        Set<String> roles,
        long membershipRevision
) {

    /**
     * 固化必填字段与角色集合。
     */
    public SessionState {
        Objects.requireNonNull(platformUserId, "platformUserId");
        roles = Set.copyOf(roles);
    }
}
