package app.runlume.admin.access.observability;

import java.util.Objects;
import java.util.UUID;

/**
 * 一次非敏感审计事实。
 *
 * <p>禁止放入 Token、Launch Code、口令、密钥和完整业务载荷。</p>
 *
 * @param actorType 行为主体类型
 * @param actorId 行为主体标识；系统动作可为空
 * @param action 稳定动作码，例如 {@code user.login}
 * @param targetType 目标类型，例如 {@code user}
 * @param targetId 目标标识，可为空
 * @param workspaceId 关联工作区，可为空
 * @param outcome 动作终态
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:40
 */
public record AuditEntry(
        ActorType actorType,
        String actorId,
        String action,
        String targetType,
        String targetId,
        UUID workspaceId,
        Outcome outcome
) {

    /**
     * 固化必填字段。
     */
    public AuditEntry {
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(outcome, "outcome");
    }

    /**
     * 审计主体类型。
     */
    public enum ActorType {
        /** 本地登录用户。 */
        USER,
        /** 平台生命周期等机器调用方。 */
        PLATFORM,
        /** 进程内部动作，例如后台任务。 */
        SYSTEM
    }

    /**
     * 审计动作终态。
     */
    public enum Outcome {
        /** 动作成功完成。 */
        SUCCESS,
        /** 动作因内部错误失败。 */
        FAILURE,
        /** 动作被鉴权或状态检查拒绝。 */
        DENIED
    }
}
