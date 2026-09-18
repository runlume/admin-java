package app.runlume.admin.access.infrastructure;

import app.runlume.admin.access.LifecycleOperationView;
import app.runlume.admin.access.WorkspaceLifecycle;
import app.runlume.admin.access.WorkspaceProblem;
import app.runlume.admin.access.WorkspaceStatus;
import app.runlume.admin.access.infrastructure.jooq.tables.records.AdminLifecycleOperationRecord;
import app.runlume.admin.access.infrastructure.jooq.tables.records.AdminWorkspaceRecord;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static app.runlume.admin.access.infrastructure.jooq.tables.AdminLifecycleOperation.ADMIN_LIFECYCLE_OPERATION;
import static app.runlume.admin.access.infrastructure.jooq.tables.AdminWorkspace.ADMIN_WORKSPACE;

/**
 * 使用 jOOQ 执行幂等的平台生命周期命令。
 *
 * <p>每个命令先持久化状态再返回结果，进程重启后仍可查询。相同幂等键绑定不同请求摘要时
 * 返回 {@code IDEMPOTENCY_CONFLICT}，不会产生第二个 workspace。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:00
 */
@Repository
public class JdbcWorkspaceLifecycle implements WorkspaceLifecycle {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String SEPARATOR = "\u001f";

    private final DSLContext dsl;

    /**
     * 创建生命周期实现。
     *
     * @param dsl jOOQ 上下文
     */
    public JdbcWorkspaceLifecycle(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public LifecycleOperationView provision(ProvisionRequest request, String idempotencyKey) {
        String digest = digest(
                "PROVISION",
                request.platformAccountId().toString(),
                request.platformAppInstanceId().toString(),
                request.moduleKey(),
                request.moduleVersion()
        );
        Optional<AdminLifecycleOperationRecord> replay = findOperationByKey(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), digest);
        }
        String externalInstanceId = registerWorkspace(request);
        return complete("PROVISION", idempotencyKey, digest, request.platformAppInstanceId(),
                externalInstanceId);
    }

    @Override
    @Transactional
    public LifecycleOperationView suspend(String externalInstanceId, String idempotencyKey) {
        return transition("SUSPEND", externalInstanceId, idempotencyKey, WorkspaceStatus.SUSPENDED);
    }

    @Override
    @Transactional
    public LifecycleOperationView resume(String externalInstanceId, String idempotencyKey) {
        return transition("RESUME", externalInstanceId, idempotencyKey, WorkspaceStatus.ACTIVE);
    }

    @Override
    @Transactional
    public LifecycleOperationView deprovision(String externalInstanceId, String idempotencyKey) {
        return transition(
                "DEPROVISION",
                externalInstanceId,
                idempotencyKey,
                WorkspaceStatus.DEPROVISIONED
        );
    }

    @Override
    public Optional<LifecycleOperationView> findOperation(UUID operationId) {
        return dsl.selectFrom(ADMIN_LIFECYCLE_OPERATION)
                .where(ADMIN_LIFECYCLE_OPERATION.ID.eq(operationId))
                .fetchOptional(JdbcWorkspaceLifecycle::toView);
    }

    private String registerWorkspace(ProvisionRequest request) {
        Optional<AdminWorkspaceRecord> existing = dsl.selectFrom(ADMIN_WORKSPACE)
                .where(ADMIN_WORKSPACE.PLATFORM_APP_INSTANCE_ID.eq(request.platformAppInstanceId()))
                .fetchOptional();
        if (existing.isPresent()) {
            AdminWorkspaceRecord workspace = existing.get();
            boolean sameBoundary = workspace.getPlatformAccountId().equals(request.platformAccountId())
                    && workspace.getModuleKey().equals(request.moduleKey());
            if (!sameBoundary) {
                throw WorkspaceProblem.of(WorkspaceProblem.Code.APP_INSTANCE_ALREADY_REGISTERED);
            }
            if (!WorkspaceStatus.ACTIVE.name().equals(workspace.getStatus())) {
                updateStatus(workspace, WorkspaceStatus.ACTIVE);
            }
            return workspace.getExternalInstanceId();
        }
        AdminWorkspaceRecord workspace = dsl.newRecord(ADMIN_WORKSPACE);
        workspace.setId(UUID.randomUUID());
        workspace.setPlatformAccountId(request.platformAccountId());
        workspace.setPlatformAppInstanceId(request.platformAppInstanceId());
        workspace.setModuleKey(request.moduleKey());
        workspace.setModuleVersion(request.moduleVersion());
        workspace.setExternalInstanceId("ws_" + UUID.randomUUID().toString().replace("-", ""));
        workspace.setStatus(WorkspaceStatus.ACTIVE.name());
        workspace.setVersion(0L);
        workspace.store();
        return workspace.getExternalInstanceId();
    }

    private LifecycleOperationView transition(
            String command,
            String externalInstanceId,
            String idempotencyKey,
            WorkspaceStatus target
    ) {
        String digest = digest(command, externalInstanceId);
        Optional<AdminLifecycleOperationRecord> replay = findOperationByKey(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), digest);
        }
        AdminWorkspaceRecord workspace = dsl.selectFrom(ADMIN_WORKSPACE)
                .where(ADMIN_WORKSPACE.EXTERNAL_INSTANCE_ID.eq(externalInstanceId))
                .fetchOptional()
                .orElseThrow(() -> WorkspaceProblem.of(WorkspaceProblem.Code.WORKSPACE_NOT_FOUND));
        updateStatus(workspace, target);
        return complete(command, idempotencyKey, digest, workspace.getPlatformAppInstanceId(),
                workspace.getExternalInstanceId());
    }

    private void updateStatus(AdminWorkspaceRecord workspace, WorkspaceStatus target) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.update(ADMIN_WORKSPACE)
                .set(ADMIN_WORKSPACE.STATUS, target.name())
                .set(ADMIN_WORKSPACE.UPDATED_AT, now)
                .set(ADMIN_WORKSPACE.VERSION, ADMIN_WORKSPACE.VERSION.plus(1))
                .set(
                        ADMIN_WORKSPACE.SUSPENDED_AT,
                        target == WorkspaceStatus.SUSPENDED ? now : null
                )
                .set(
                        ADMIN_WORKSPACE.DEPROVISIONED_AT,
                        target == WorkspaceStatus.DEPROVISIONED ? now : null
                )
                .where(ADMIN_WORKSPACE.ID.eq(workspace.getId()))
                .execute();
    }

    private LifecycleOperationView complete(
            String command,
            String idempotencyKey,
            String digest,
            UUID platformAppInstanceId,
            String externalInstanceId
    ) {
        AdminLifecycleOperationRecord operation = dsl.newRecord(ADMIN_LIFECYCLE_OPERATION);
        operation.setId(UUID.randomUUID());
        operation.setIdempotencyKey(idempotencyKey);
        operation.setCommand(command);
        operation.setRequestDigest(digest);
        operation.setState("SUCCEEDED");
        operation.setPlatformAppInstanceId(platformAppInstanceId);
        operation.setExternalInstanceId(externalInstanceId);
        operation.setCompletedAt(OffsetDateTime.now());
        try {
            operation.store();
        } catch (DataAccessException exception) {
            // 并发重试时唯一键会先被对方写入；此时按幂等语义返回对方结果。
            Optional<AdminLifecycleOperationRecord> existing = findOperationByKey(idempotencyKey);
            if (isUniqueViolation(exception) && existing.isPresent()) {
                return toView(existing.get());
            }
            throw exception;
        }
        return toView(operation);
    }

    private LifecycleOperationView replay(AdminLifecycleOperationRecord operation, String digest) {
        if (!operation.getRequestDigest().equals(digest)) {
            throw WorkspaceProblem.of(WorkspaceProblem.Code.IDEMPOTENCY_CONFLICT);
        }
        return toView(operation);
    }

    private Optional<AdminLifecycleOperationRecord> findOperationByKey(String idempotencyKey) {
        return dsl.selectFrom(ADMIN_LIFECYCLE_OPERATION)
                .where(ADMIN_LIFECYCLE_OPERATION.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional();
    }

    private static boolean isUniqueViolation(DataAccessException exception) {
        return exception.getCause() instanceof java.sql.SQLException sql
                && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }

    private static LifecycleOperationView toView(AdminLifecycleOperationRecord record) {
        return new LifecycleOperationView(
                record.getId(),
                record.getCommand(),
                record.getState(),
                record.getPlatformAppInstanceId(),
                record.getExternalInstanceId(),
                record.getFailureCode(),
                JdbcWorkspaceDirectory.toInstant(record.getCreatedAt()),
                JdbcWorkspaceDirectory.toInstant(record.getCompletedAt())
        );
    }

    private static String digest(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update(SEPARATOR.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform.", exception);
        }
    }
}
