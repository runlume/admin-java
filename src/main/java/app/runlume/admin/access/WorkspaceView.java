package app.runlume.admin.access;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 平台 AppInstance 与本地 workspace 映射的不可变视图。
 *
 * @param id 本地 workspace 标识
 * @param platformAccountId 平台 Account 标识
 * @param platformAppInstanceId 平台 AppInstance 标识
 * @param moduleKey 平台模块 Key
 * @param moduleVersion 平台模块版本
 * @param externalInstanceId 返回给平台的不透明实例标识
 * @param status 当前状态
 * @param createdAt 创建时间
 * @param updatedAt 最近更新时间
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:00
 */
public record WorkspaceView(
        UUID id,
        UUID platformAccountId,
        UUID platformAppInstanceId,
        String moduleKey,
        String moduleVersion,
        String externalInstanceId,
        WorkspaceStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * 固化必填字段。
     */
    public WorkspaceView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(platformAccountId, "platformAccountId");
        Objects.requireNonNull(platformAppInstanceId, "platformAppInstanceId");
        Objects.requireNonNull(moduleKey, "moduleKey");
        Objects.requireNonNull(externalInstanceId, "externalInstanceId");
        Objects.requireNonNull(status, "status");
    }

    /**
     * 判断当前 workspace 是否接受新的本地会话。
     *
     * @return 仅 ACTIVE 时为 true
     */
    public boolean acceptsNewSession() {
        return status == WorkspaceStatus.ACTIVE;
    }
}
