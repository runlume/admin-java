package app.runlume.admin.access;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 平台生命周期命令的操作结果。
 *
 * @param id 操作标识
 * @param command 命令名
 * @param state 操作状态
 * @param platformAppInstanceId 平台 AppInstance 标识，可为空
 * @param externalInstanceId 不透明实例标识，可为空
 * @param failureCode 稳定失败码，成功时为空
 * @param createdAt 创建时间
 * @param completedAt 完成时间，未完成时为空
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:00
 */
public record LifecycleOperationView(
        UUID id,
        String command,
        String state,
        UUID platformAppInstanceId,
        String externalInstanceId,
        String failureCode,
        Instant createdAt,
        Instant completedAt
) {

    /**
     * 固化必填字段。
     */
    public LifecycleOperationView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(state, "state");
    }
}
