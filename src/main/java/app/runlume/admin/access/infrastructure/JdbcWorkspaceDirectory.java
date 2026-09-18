package app.runlume.admin.access.infrastructure;

import app.runlume.admin.access.WorkspaceDirectory;
import app.runlume.admin.access.WorkspaceStatus;
import app.runlume.admin.access.WorkspaceView;
import app.runlume.admin.access.infrastructure.jooq.tables.records.AdminWorkspaceRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.runlume.admin.access.infrastructure.jooq.tables.AdminWorkspace.ADMIN_WORKSPACE;

/**
 * 使用 jOOQ 读取工作区映射。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:00
 */
@Repository
public class JdbcWorkspaceDirectory implements WorkspaceDirectory {

    private static final String WORKSPACE_SOURCE_PLATFORM = "PLATFORM";

    private final DSLContext dsl;

    /**
     * 创建工作区读取实现。
     *
     * @param dsl jOOQ 上下文
     */
    public JdbcWorkspaceDirectory(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<WorkspaceView> findByAppInstance(UUID platformAppInstanceId) {
        return dsl.selectFrom(ADMIN_WORKSPACE)
                .where(ADMIN_WORKSPACE.PLATFORM_APP_INSTANCE_ID.eq(platformAppInstanceId))
                .fetchOptional(JdbcWorkspaceDirectory::toView);
    }

    @Override
    public Optional<WorkspaceView> find(UUID platformAccountId, UUID platformAppInstanceId) {
        return dsl.selectFrom(ADMIN_WORKSPACE)
                .where(ADMIN_WORKSPACE.PLATFORM_ACCOUNT_ID.eq(platformAccountId))
                .and(ADMIN_WORKSPACE.PLATFORM_APP_INSTANCE_ID.eq(platformAppInstanceId))
                .fetchOptional(JdbcWorkspaceDirectory::toView);
    }

    @Override
    public Optional<WorkspaceView> findByExternalInstanceId(String externalInstanceId) {
        return dsl.selectFrom(ADMIN_WORKSPACE)
                .where(ADMIN_WORKSPACE.EXTERNAL_INSTANCE_ID.eq(externalInstanceId))
                .fetchOptional(JdbcWorkspaceDirectory::toView);
    }

    @Override
    public List<WorkspaceView> list(int offset, int limit) {
        return dsl.selectFrom(ADMIN_WORKSPACE)
                .where(ADMIN_WORKSPACE.SOURCE.eq(WORKSPACE_SOURCE_PLATFORM))
                .orderBy(ADMIN_WORKSPACE.CREATED_AT.desc(), ADMIN_WORKSPACE.ID.asc())
                .limit(limit)
                .offset(Math.max(offset, 0))
                .fetch(JdbcWorkspaceDirectory::toView);
    }

    @Override
    public long count() {
        return dsl.fetchCount(ADMIN_WORKSPACE, ADMIN_WORKSPACE.SOURCE.eq(WORKSPACE_SOURCE_PLATFORM));
    }

    @Override
    public Optional<WorkspaceStatus> statusOf(UUID workspaceId) {
        return dsl.select(ADMIN_WORKSPACE.STATUS)
                .from(ADMIN_WORKSPACE)
                .where(ADMIN_WORKSPACE.ID.eq(workspaceId))
                .fetchOptional(ADMIN_WORKSPACE.STATUS)
                .map(WorkspaceStatus::valueOf);
    }

    static WorkspaceView toView(AdminWorkspaceRecord record) {
        return new WorkspaceView(
                record.getId(),
                record.getPlatformAccountId(),
                record.getPlatformAppInstanceId(),
                record.getModuleKey(),
                record.getModuleVersion(),
                record.getExternalInstanceId(),
                WorkspaceStatus.valueOf(record.getStatus()),
                toInstant(record.getCreatedAt()),
                toInstant(record.getUpdatedAt())
        );
    }

    static Instant toInstant(java.time.OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
