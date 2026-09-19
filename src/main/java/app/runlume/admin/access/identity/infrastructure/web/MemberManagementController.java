package app.runlume.admin.access.identity.infrastructure.web;

import app.runlume.admin.access.identity.AdminIdentity;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.admin.access.identity.AdminUserView;
import app.runlume.admin.access.identity.IdentityProblem;
import app.runlume.admin.access.identity.UserStatus;
import app.runlume.admin.access.identity.infrastructure.security.AdminSessionRevocation;
import app.runlume.admin.access.observability.AdminAuditLog;
import app.runlume.admin.access.observability.AuditEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import java.util.Set;
import java.util.UUID;

/**
 * 工作区成员与本地角色管理入口。
 *
 * <p>所有端点都以当前会话的工作区为边界：列表、详情与写入只能作用于本工作区成员，
 * 平台派生的管理员角色不在这里增删。禁用成员时同时撤销该成员的全部既有会话。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:50
 */
@RestController
@RequestMapping("/api/v1")
public class MemberManagementController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminIdentity identity;
    private final AdminSessionRevocation sessionRevocation;
    private final AdminAuditLog auditLog;

    /**
     * 创建账号管理控制器。
     *
     * @param identity 本地身份入口
     * @param sessionRevocation 会话撤销器
     * @param auditLog 审计入口
     */
    public MemberManagementController(
            AdminIdentity identity,
            AdminSessionRevocation sessionRevocation,
            AdminAuditLog auditLog
    ) {
        this.identity = identity;
        this.sessionRevocation = sessionRevocation;
        this.auditLog = auditLog;
    }

    /**
     * 分页查询当前工作区成员。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @param keyword 邮箱或名称关键字
     * @param principal 当前会话主体；只列该工作区的成员
     * @return 分页结果
     */
    @GetMapping("/members")
    @PreAuthorize("hasAuthority('example.admin.member.view')")
    public MemberListResponse listMembers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AdminSessionPrincipal principal
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        List<MemberResponse> items = identity
                .listUsers(
                        principal.workspaceId(),
                        (safePage - 1) * safeSize,
                        safeSize,
                        keyword
                )
                .stream()
                .map(MemberResponse::from)
                .toList();
        return new MemberListResponse(
                items,
                identity.countUsers(principal.workspaceId(), keyword),
                safePage,
                safeSize
        );
    }

    /**
     * 读取单个账号。
     *
     * @param id 成员标识
     * @param principal 当前会话主体；只能读取本工作区成员
     * @return 账号信息
     */
    @GetMapping("/members/{id}")
    @PreAuthorize("hasAuthority('example.admin.member.view')")
    public MemberResponse getMember(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminSessionPrincipal principal
    ) {
        return identity.findUser(principal.workspaceId(), id)
                .map(MemberResponse::from)
                .orElseThrow(() -> IdentityProblem.of(IdentityProblem.Code.USER_NOT_FOUND));
    }

    /**
     * 修改成员展示名称与本地角色。
     *
     * @param id 成员标识
     * @param principal 当前会话主体；只能修改本工作区成员
     * @param body 修改请求
     * @return 更新后的成员信息
     */
    @PatchMapping("/members/{id}")
    @PreAuthorize("hasAuthority('example.admin.member.update')")
    public MemberResponse updateMember(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMemberRequest body,
            @AuthenticationPrincipal AdminSessionPrincipal principal
    ) {
        AdminUserView user = identity.findUser(principal.workspaceId(), id)
                .orElseThrow(() -> IdentityProblem.of(IdentityProblem.Code.USER_NOT_FOUND));
        if (body.displayName() != null && !body.displayName().isBlank()) {
            user = identity.renameUser(principal.workspaceId(), id, body.displayName());
        }
        if (body.roles() != null) {
            user = identity.changeRoles(principal.workspaceId(), id, body.roles());
        }
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.USER,
                null,
                "member.update",
                "member",
                id.toString(),
                principal.workspaceId(),
                AuditEntry.Outcome.SUCCESS
        ));
        return MemberResponse.from(user);
    }

    /**
     * 启用或禁用账号，禁用时立即撤销该账号的全部会话。
     *
     * @param id 成员标识
     * @param principal 当前会话主体；只能启用或禁用本工作区成员
     * @param body 状态请求
     * @return 更新后的成员信息
     */
    @PostMapping("/members/{id}/status")
    @PreAuthorize("hasAuthority('example.admin.member.disable')")
    public MemberResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeMemberStatusRequest body,
            @AuthenticationPrincipal AdminSessionPrincipal principal
    ) {
        AdminUserView user = identity.changeStatus(
                principal.workspaceId(),
                id,
                body.status()
        );
        if (body.status() == UserStatus.DISABLED) {
            sessionRevocation.revokeAll(user.email());
        }
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.USER,
                null,
                "member.status",
                "member",
                id.toString(),
                principal.workspaceId(),
                AuditEntry.Outcome.SUCCESS
        ));
        return MemberResponse.from(user);
    }

    /**
     * 列出全部角色。
     *
     * @return 角色信息
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('example.admin.role.view')")
    public List<RoleResponse> listRoles() {
        return identity.listRoles().stream().map(RoleResponse::from).toList();
    }

    /**
     * 账号信息响应。
     *
     * @param id 账号标识
     * @param email 登录邮箱
     * @param displayName 展示名称
     * @param status 账号状态
     * @param roles 角色码
     * @param permissions 权限码
     * @param createdAt 创建时间
     * @param lastLoginAt 最近登录时间
     */
    public record MemberResponse(
            String id,
            String email,
            String displayName,
            String status,
            Set<String> roles,
            Set<String> permissions,
            Instant createdAt,
            Instant lastLoginAt
    ) {

        /**
         * 由账号视图构造响应。
         *
         * @param user 账号视图
         * @return 账号信息
         */
        public static MemberResponse from(AdminUserView user) {
            return new MemberResponse(
                    user.id().toString(),
                    user.email(),
                    user.displayName(),
                    user.status().name(),
                    user.roles(),
                    user.permissions(),
                    user.createdAt(),
                    user.lastLoginAt()
            );
        }
    }

    /**
     * 账号分页响应。
     *
     * @param items 当前页账号
     * @param total 账号总数
     * @param page 当前页码
     * @param size 每页条数
     */
    public record MemberListResponse(
            List<MemberResponse> items,
            long total,
            int page,
            int size
    ) {
    }

    /**
     * 角色信息响应。
     *
     * @param id 角色标识
     * @param code 角色码
     * @param name 展示名称
     * @param description 说明
     * @param builtIn 是否内置
     * @param permissions 权限码
     */
    public record RoleResponse(
            String id,
            String code,
            String name,
            String description,
            boolean builtIn,
            Set<String> permissions
    ) {

        /**
         * 由角色视图构造响应。
         *
         * @param role 角色视图
         * @return 角色信息
         */
        public static RoleResponse from(
                app.runlume.admin.access.identity.AdminRoleView role
        ) {
            return new RoleResponse(
                    role.id().toString(),
                    role.code(),
                    role.name(),
                    role.description(),
                    role.builtIn(),
                    role.permissionCodes()
            );
        }
    }

    /**
     * 修改成员请求。
     *
     * @param displayName 新展示名称，可为空
     * @param roles 新的本地角色集合，可为空；平台派生角色不在这里增删
     */
    public record UpdateMemberRequest(
            @Size(max = 200) String displayName,
            Set<String> roles
    ) {
    }

    /**
     * 修改成员状态请求。
     *
     * @param status 目标状态
     */
    public record ChangeMemberStatusRequest(
            @jakarta.validation.constraints.NotNull UserStatus status
    ) {
    }
}
