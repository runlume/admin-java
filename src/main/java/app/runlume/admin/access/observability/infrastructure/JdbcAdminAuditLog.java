package app.runlume.admin.access.observability.infrastructure;

import app.runlume.admin.access.observability.AdminAuditLog;
import app.runlume.admin.access.observability.AuditEntry;
import app.runlume.admin.access.observability.RequestCorrelation;
import app.runlume.admin.access.observability.infrastructure.jooq.tables.records.AdminAuditEventRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static app.runlume.admin.access.observability.infrastructure.jooq.tables.AdminAuditEvent.ADMIN_AUDIT_EVENT;

/**
 * 使用 jOOQ 追加审计事实。
 *
 * <p>审计使用独立事务，保证业务回滚时登录失败等安全事实仍然留痕。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:40
 */
@Repository
public class JdbcAdminAuditLog implements AdminAuditLog {

    private final DSLContext dsl;

    /**
     * 创建审计写入实现。
     *
     * @param dsl jOOQ 上下文
     */
    public JdbcAdminAuditLog(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEntry entry) {
        AdminAuditEventRecord record = dsl.newRecord(ADMIN_AUDIT_EVENT);
        record.setId(UUID.randomUUID());
        record.setActorType(entry.actorType().name());
        record.setActorId(entry.actorId());
        record.setAction(entry.action());
        record.setTargetType(entry.targetType());
        record.setTargetId(entry.targetId());
        record.setWorkspaceId(entry.workspaceId());
        record.setCorrelationId(currentCorrelationId());
        record.setOutcome(entry.outcome().name());
        record.store();
    }

    private static String currentCorrelationId() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return RequestCorrelation.current(attributes.getRequest()).orElse(null);
        }
        return null;
    }
}
