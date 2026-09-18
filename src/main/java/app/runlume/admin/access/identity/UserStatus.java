package app.runlume.admin.access.identity;

/**
 * 本地账号状态。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:50
 */
public enum UserStatus {

    /** 允许登录并建立会话。 */
    ACTIVE,

    /** 拒绝登录，且既有会话在下次请求时立即失效。 */
    DISABLED
}
