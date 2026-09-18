package app.runlume.admin.notice.infrastructure.web;

import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.admin.access.observability.AdminAuditLog;
import app.runlume.admin.access.observability.AuditEntry;
import app.runlume.admin.notice.Notice;
import app.runlume.admin.notice.NoticeDirectory;
import app.runlume.admin.notice.NoticeProblem;
import app.runlume.admin.notice.NoticeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 公告业务域入口，演示业务域如何消费会话主体与写审计。
 *
 * <p>工作区范围只取自 {@link AdminSessionPrincipal}，请求参数无法覆盖。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:20
 */
@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NoticeDirectory notices;
    private final AdminAuditLog auditLog;

    /**
     * 创建公告控制器。
     *
     * @param notices 公告读写入口
     * @param auditLog 审计入口
     */
    public NoticeController(NoticeDirectory notices, AdminAuditLog auditLog) {
        this.notices = notices;
        this.auditLog = auditLog;
    }

    /**
     * 分页查询可见公告。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @param status 状态过滤
     * @param principal 当前会话主体
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('example.admin.notice.view')")
    public NoticeListResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) NoticeStatus status,
            @AuthenticationPrincipal AdminSessionPrincipal principal
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        List<NoticeResponse> items = notices
                .list((safePage - 1) * safeSize, safeSize, status, principal.workspaceId())
                .stream()
                .map(NoticeResponse::from)
                .toList();
        return new NoticeListResponse(
                items,
                notices.count(status, principal.workspaceId()),
                safePage,
                safeSize
        );
    }

    /**
     * 读取单条公告。
     *
     * @param id 公告标识
     * @param principal 当前会话主体
     * @return 公告信息
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('example.admin.notice.view')")
    public NoticeResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminSessionPrincipal principal
    ) {
        return notices.find(id, principal.workspaceId())
                .map(NoticeResponse::from)
                .orElseThrow(() -> NoticeProblem.of(NoticeProblem.Code.NOTICE_NOT_FOUND));
    }

    /**
     * 创建草稿公告。
     *
     * @param body 创建请求
     * @param principal 当前会话主体
     * @return 新公告
     */
    @PostMapping
    @PreAuthorize("hasAuthority('example.admin.notice.manage')")
    public ResponseEntity<NoticeResponse> create(
            @Valid @RequestBody CreateNoticeRequest body,
            @AuthenticationPrincipal AdminSessionPrincipal principal
    ) {
        Notice notice = notices.create(
                body.title(),
                body.body(),
                principal.workspaceId(),
                principal.userId()
        );
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.USER,
                principal.userId().toString(),
                "notice.create",
                "notice",
                notice.id().toString(),
                notice.workspaceId(),
                AuditEntry.Outcome.SUCCESS
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(NoticeResponse.from(notice));
    }

    /**
     * 修改草稿公告。
     *
     * @param id 公告标识
     * @param body 修改请求
     * @param principal 当前会话主体
     * @return 更新后的公告
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('example.admin.notice.manage')")
    public NoticeResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNoticeRequest body,
            @AuthenticationPrincipal AdminSessionPrincipal principal
    ) {
        Notice notice = notices.update(
                id,
                principal.workspaceId(),
                body.title(),
                body.body()
        );
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.USER,
                principal.userId().toString(),
                "notice.update",
                "notice",
                id.toString(),
                notice.workspaceId(),
                AuditEntry.Outcome.SUCCESS
        ));
        return NoticeResponse.from(notice);
    }

    /**
     * 发布或归档公告。
     *
     * @param id 公告标识
     * @param body 状态请求
     * @param principal 当前会话主体
     * @return 更新后的公告
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('example.admin.notice.manage')")
    public NoticeResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeNoticeStatusRequest body,
            @AuthenticationPrincipal AdminSessionPrincipal principal
    ) {
        Notice notice = notices.changeStatus(id, principal.workspaceId(), body.status());
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.USER,
                principal.userId().toString(),
                "notice.status",
                "notice",
                id.toString(),
                notice.workspaceId(),
                AuditEntry.Outcome.SUCCESS
        ));
        return NoticeResponse.from(notice);
    }

    /**
     * 公告响应。
     *
     * @param id 公告标识
     * @param title 标题
     * @param body 正文
     * @param status 状态
     * @param workspaceId 归属工作区
     * @param publishedAt 发布时间
     * @param createdAt 创建时间
     * @param updatedAt 最近更新时间
     */
    public record NoticeResponse(
            String id,
            String title,
            String body,
            String status,
            String workspaceId,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {

        /**
         * 由公告构造响应。
         *
         * @param notice 公告
         * @return 公告响应
         */
        public static NoticeResponse from(Notice notice) {
            return new NoticeResponse(
                    notice.id().toString(),
                    notice.title(),
                    notice.body(),
                    notice.status().name(),
                    notice.workspaceId() == null ? null : notice.workspaceId().toString(),
                    notice.publishedAt(),
                    notice.createdAt(),
                    notice.updatedAt()
            );
        }
    }

    /**
     * 公告分页响应。
     *
     * @param items 当前页公告
     * @param total 公告总数
     * @param page 当前页码
     * @param size 每页条数
     */
    public record NoticeListResponse(
            List<NoticeResponse> items,
            long total,
            int page,
            int size
    ) {
    }

    /**
     * 创建公告请求。
     *
     * @param title 标题
     * @param body 正文
     */
    public record CreateNoticeRequest(
            @NotBlank @Size(min = 1, max = 200) String title,
            @NotBlank @Size(max = 20000) String body
    ) {
    }

    /**
     * 修改公告请求。
     *
     * @param title 标题
     * @param body 正文
     */
    public record UpdateNoticeRequest(
            @NotBlank @Size(min = 1, max = 200) String title,
            @NotBlank @Size(max = 20000) String body
    ) {
    }

    /**
     * 变更公告状态请求。
     *
     * @param status 目标状态
     */
    public record ChangeNoticeStatusRequest(@NotNull NoticeStatus status) {
    }
}
