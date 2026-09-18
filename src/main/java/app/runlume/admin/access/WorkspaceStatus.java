package app.runlume.admin.access;

/**
 * 业务 workspace 生命周期状态。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:00
 */
public enum WorkspaceStatus {

    /** 允许新会话与业务写。 */
    ACTIVE,

    /** 拒绝新会话，并阻止每一次业务写。 */
    SUSPENDED,

    /** 正在注销，拒绝新会话与业务写。 */
    DEPROVISIONING,

    /** 已注销，立即撤销访问。 */
    DEPROVISIONED
}
