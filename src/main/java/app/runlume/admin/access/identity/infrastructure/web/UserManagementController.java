package app.runlume.admin.access.identity.infrastructure.web;

import app.runlume.admin.access.identity.AdminIdentity;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 账号与角色管理入口。
 *
 * <p>每个端点都要求对应的具体权限码；禁用账号时同时撤销该账号的全部既有会话。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:50
 */
@RestController
@RequestMapping("/api/v1")
public class UserManagementController {

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
    public UserManagementController(
            AdminIdentity identity,
            AdminSessionRevocation sessionRevocation,
            AdminAuditLog auditLog
    ) {
        this.identity = identity;
        this.sessionRevocation = sessionRevocation;
        this.auditLog = auditLog;
    }

    /**
     * 分页查询账号。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @param keyword 邮箱或名称关键字
     * @return 分页结果
     */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:view')")
    public UserListResponse listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        List<UserResponse> items = identity
                .listUsers((safePage - 1) * safeSize, safeSize, keyword)
                .stream()
                .map(UserResponse::from)
                .toList();
        return new UserListResponse(
                items,
                identity.countUsers(keyword),
                safePage,
                safeSize
        );
    }

    /**
     * 读取单个账号。
     *
     * @param id 账号标识
     * @return 账号信息
     */
    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:view')")
    public UserResponse getUser(@PathVariable UUID id) {
        return identity.findUser(id)
                .map(UserResponse::from)
                .orElseThrow(() -> IdentityProblem.of(IdentityProblem.Code.USER_NOT_FOUND));
    }

    /**
     * 创建账号。
     *
     * @param body 创建请求
     * @return 新账号信息
     */
    @PostMapping("/users")
    @PreAuthorize("hasAuthority('user:create')")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest body
    ) {
        AdminUserView user = identity.createUser(
                body.email(),
                body.displayName(),
                body.password(),
                body.roles() == null ? Set.of() : body.roles()
        );
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.USER,
                null,
                "user.create",
                "user",
                user.id().toString(),
                null,
                AuditEntry.Outcome.SUCCESS
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    /**
     * 修改账号展示名称与角色。
     *
     * @param id 账号标识
     * @param body 修改请求
     * @return 更新后的账号信息
     */
    @PatchMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    public UserResponse updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest body
    ) {
        AdminUserView user = identity.findUser(id)
                .orElseThrow(() -> IdentityProblem.of(IdentityProblem.Code.USER_NOT_FOUND));
        if (body.displayName() != null && !body.displayName().isBlank()) {
            user = identity.renameUser(id, body.displayName());
        }
        if (body.roles() != null) {
            user = identity.changeRoles(id, body.roles());
        }
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.USER,
                null,
                "user.update",
                "user",
                id.toString(),
                null,
                AuditEntry.Outcome.SUCCESS
        ));
        return UserResponse.from(user);
    }

    /**
     * 启用或禁用账号，禁用时立即撤销该账号的全部会话。
     *
     * @param id 账号标识
     * @param body 状态请求
     * @return 更新后的账号信息
     */
    @PostMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('user:disable')")
    public UserResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest body
    ) {
        AdminUserView user = identity.changeStatus(id, body.status());
        if (body.status() == UserStatus.DISABLED) {
            sessionRevocation.revokeAll(user.email());
        }
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.USER,
                null,
                "user.status",
                "user",
                id.toString(),
                null,
                AuditEntry.Outcome.SUCCESS
        ));
        return UserResponse.from(user);
    }

    /**
     * 列出全部角色。
     *
     * @return 角色信息
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:view')")
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
    public record UserResponse(
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
        public static UserResponse from(AdminUserView user) {
            return new UserResponse(
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
    public record UserListResponse(List<UserResponse> items, long total, int page, int size) {
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
     * 创建账号请求。
     *
     * @param email 登录邮箱
     * @param displayName 展示名称
     * @param password 明文口令
     * @param roles 角色码，可为空
     */
    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String displayName,
            @NotBlank @Size(min = 12, max = 200) String password,
            Set<String> roles
    ) {
    }

    /**
     * 修改账号请求。
     *
     * @param displayName 新展示名称，可为空
     * @param roles 新角色集合，可为空
     */
    public record UpdateUserRequest(
            @Size(max = 200) String displayName,
            Set<String> roles
    ) {
    }

    /**
     * 修改账号状态请求。
     *
     * @param status 目标状态
     */
    public record ChangeStatusRequest(
            @jakarta.validation.constraints.NotNull UserStatus status
    ) {
    }
}
