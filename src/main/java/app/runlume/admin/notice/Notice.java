package app.runlume.admin.notice;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 公告只读视图。
 *
 * @param id 公告标识
 * @param title 标题
 * @param body 正文
 * @param status 状态
 * @param workspaceId 归属工作区；为空表示后台公共公告
 * @param createdBy 创建人本地账号标识
 * @param publishedAt 发布时间，可为空
 * @param createdAt 创建时间
 * @param updatedAt 最近更新时间
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:20
 */
public record Notice(
        UUID id,
        String title,
        String body,
        NoticeStatus status,
        UUID workspaceId,
        UUID createdBy,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * 固化必填字段。
     */
    public Notice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdBy, "createdBy");
    }
}
