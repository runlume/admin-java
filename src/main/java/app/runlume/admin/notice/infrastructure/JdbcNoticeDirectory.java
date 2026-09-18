package app.runlume.admin.notice.infrastructure;

import app.runlume.admin.notice.Notice;
import app.runlume.admin.notice.NoticeDirectory;
import app.runlume.admin.notice.NoticeProblem;
import app.runlume.admin.notice.NoticeStatus;
import app.runlume.admin.notice.infrastructure.jooq.tables.records.AdminNoticeRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static app.runlume.admin.notice.infrastructure.jooq.tables.AdminNotice.ADMIN_NOTICE;

/**
 * 使用 jOOQ 实现公告读写。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:20
 */
@Repository
public class JdbcNoticeDirectory implements NoticeDirectory {

    private final DSLContext dsl;

    /**
     * 创建公告读写实现。
     *
     * @param dsl jOOQ 上下文
     */
    public JdbcNoticeDirectory(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<Notice> list(int offset, int limit, NoticeStatus status, UUID workspaceId) {
        return dsl.selectFrom(ADMIN_NOTICE)
                .where(visibleTo(workspaceId).and(statusFilter(status)))
                .orderBy(ADMIN_NOTICE.CREATED_AT.desc(), ADMIN_NOTICE.ID.asc())
                .limit(Math.max(limit, 1))
                .offset(Math.max(offset, 0))
                .fetch(JdbcNoticeDirectory::toView);
    }

    @Override
    public long count(NoticeStatus status, UUID workspaceId) {
        return dsl.fetchCount(dsl.selectFrom(ADMIN_NOTICE)
                .where(visibleTo(workspaceId).and(statusFilter(status))));
    }

    @Override
    public Optional<Notice> find(UUID id, UUID workspaceId) {
        return dsl.selectFrom(ADMIN_NOTICE)
                .where(ADMIN_NOTICE.ID.eq(id).and(visibleTo(workspaceId)))
                .fetchOptional(JdbcNoticeDirectory::toView);
    }

    @Override
    @Transactional
    public Notice create(String title, String body, UUID workspaceId, UUID authorId) {
        AdminNoticeRecord record = dsl.newRecord(ADMIN_NOTICE);
        record.setId(UUID.randomUUID());
        record.setTitle(title);
        record.setBody(body);
        record.setStatus(NoticeStatus.DRAFT.name());
        record.setWorkspaceId(workspaceId);
        record.setCreatedBy(authorId);
        record.setVersion(0L);
        record.store();
        return toView(record);
    }

    @Override
    @Transactional
    public Notice update(UUID id, UUID workspaceId, String title, String body) {
        AdminNoticeRecord record = requireEditable(id, workspaceId);
        dsl.update(ADMIN_NOTICE)
                .set(ADMIN_NOTICE.TITLE, title)
                .set(ADMIN_NOTICE.BODY, body)
                .set(ADMIN_NOTICE.UPDATED_AT, OffsetDateTime.now())
                .set(ADMIN_NOTICE.VERSION, ADMIN_NOTICE.VERSION.plus(1))
                .where(ADMIN_NOTICE.ID.eq(record.getId()))
                .execute();
        return require(id, workspaceId);
    }

    @Override
    @Transactional
    public Notice changeStatus(UUID id, UUID workspaceId, NoticeStatus status) {
        require(id, workspaceId);
        OffsetDateTime now = OffsetDateTime.now();
        dsl.update(ADMIN_NOTICE)
                .set(ADMIN_NOTICE.STATUS, status.name())
                .set(ADMIN_NOTICE.UPDATED_AT, now)
                .set(
                        ADMIN_NOTICE.PUBLISHED_AT,
                        status == NoticeStatus.PUBLISHED ? now : null
                )
                .set(ADMIN_NOTICE.VERSION, ADMIN_NOTICE.VERSION.plus(1))
                .where(ADMIN_NOTICE.ID.eq(id))
                .execute();
        return require(id, workspaceId);
    }

    private AdminNoticeRecord requireEditable(UUID id, UUID workspaceId) {
        AdminNoticeRecord record = dsl.selectFrom(ADMIN_NOTICE)
                .where(ADMIN_NOTICE.ID.eq(id).and(visibleTo(workspaceId)))
                .fetchOptional()
                .orElseThrow(() -> NoticeProblem.of(NoticeProblem.Code.NOTICE_NOT_FOUND));
        if (!NoticeStatus.DRAFT.name().equals(record.getStatus())) {
            throw NoticeProblem.of(NoticeProblem.Code.NOTICE_STATE_INVALID);
        }
        return record;
    }

    private Notice require(UUID id, UUID workspaceId) {
        return find(id, workspaceId).orElseThrow(
                () -> NoticeProblem.of(NoticeProblem.Code.NOTICE_NOT_FOUND)
        );
    }

    private static Condition visibleTo(UUID workspaceId) {
        if (workspaceId == null) {
            return ADMIN_NOTICE.WORKSPACE_ID.isNull();
        }
        return ADMIN_NOTICE.WORKSPACE_ID.isNull()
                .or(ADMIN_NOTICE.WORKSPACE_ID.eq(workspaceId));
    }

    private static Condition statusFilter(NoticeStatus status) {
        return status == null
                ? org.jooq.impl.DSL.noCondition()
                : ADMIN_NOTICE.STATUS.eq(status.name());
    }

    private static Notice toView(AdminNoticeRecord record) {
        return new Notice(
                record.getId(),
                record.getTitle(),
                record.getBody(),
                NoticeStatus.valueOf(record.getStatus()),
                record.getWorkspaceId(),
                record.getCreatedBy(),
                toInstant(record.getPublishedAt()),
                toInstant(record.getCreatedAt()),
                toInstant(record.getUpdatedAt())
        );
    }

    private static java.time.Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
