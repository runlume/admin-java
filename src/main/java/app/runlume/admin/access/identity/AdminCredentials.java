package app.runlume.admin.access.identity;

import java.util.Objects;

/**
 * 口令校验所需的账号凭据，只在身份边界内流转。
 *
 * @param user 账号视图
 * @param passwordHash BCrypt 口令摘要
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:50
 */
public record AdminCredentials(AdminUserView user, String passwordHash) {

    /**
     * 固化必填字段。
     */
    public AdminCredentials {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(passwordHash, "passwordHash");
    }
}
