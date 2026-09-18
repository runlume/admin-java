package app.runlume.admin.access.identity.infrastructure.web;

import app.runlume.admin.access.identity.AdminIdentity;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.admin.access.identity.AdminUserView;
import app.runlume.admin.access.identity.IdentityProblem;
import app.runlume.admin.access.identity.LocalAccountProperties;
import app.runlume.admin.access.identity.PermissionCatalog;
import app.runlume.admin.access.identity.infrastructure.security.AdminUserDetails;
import app.runlume.admin.access.identity.infrastructure.security.LocalSessionEstablisher;
import app.runlume.admin.access.observability.AdminAuditLog;
import app.runlume.admin.access.observability.AuditEntry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 本地会话入口：注册、登录、退出、当前用户与 CSRF。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:40
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private final AdminIdentity identity;
    private final AuthenticationManager authenticationManager;
    private final LocalSessionEstablisher sessionEstablisher;
    private final LocalAccountProperties properties;
    private final AdminAuditLog auditLog;
    private final Clock clock;

    /**
     * 创建会话控制器。
     *
     * @param identity 本地身份入口
     * @param authenticationManager 口令认证管理器
     * @param sessionEstablisher 会话建立器
     * @param properties 本地账号配置
     * @param auditLog 审计入口
     * @param clock 判定时间源
     */
    public SessionController(
            AdminIdentity identity,
            AuthenticationManager authenticationManager,
            LocalSessionEstablisher sessionEstablisher,
            LocalAccountProperties properties,
            AdminAuditLog auditLog,
            Clock clock
    ) {
        this.identity = identity;
        this.authenticationManager = authenticationManager;
        this.sessionEstablisher = sessionEstablisher;
        this.properties = properties;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * 读取 CSRF 令牌，写请求必须回传同名请求头。
     *
     * @param csrfToken 当前 CSRF 令牌
     * @return 令牌与请求头名称
     */
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    /**
     * 自助注册并直接建立会话。
     *
     * @param body 注册请求
     * @param request 当前请求
     * @param response 当前响应
     * @return 会话响应
     */
    @PostMapping("/auth/register")
    public ResponseEntity<SessionResponse> register(
            @Valid @RequestBody RegisterRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (!properties.registrationEnabled()) {
            throw IdentityProblem.of(IdentityProblem.Code.REGISTRATION_DISABLED);
        }
        AdminUserView user = identity.register(
                body.email(),
                body.displayName(),
                body.password()
        );
        sessionEstablisher.establish(user, null, null, 0L, sessionExpiry(), request, response);
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.USER,
                user.id().toString(),
                "user.register",
                "user",
                user.id().toString(),
                null,
                AuditEntry.Outcome.SUCCESS
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.from(user));
    }

    /**
     * 使用邮箱与口令登录。
     *
     * @param body 登录请求
     * @param request 当前请求
     * @param response 当前响应
     * @return 会话响应
     */
    @PostMapping("/auth/login")
    public SessionResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            body.email().trim().toLowerCase(Locale.ROOT),
                            body.password()
                    )
            );
        } catch (DisabledException exception) {
            auditLog.record(loginAudit(null, AuditEntry.Outcome.DENIED));
            throw IdentityProblem.of(IdentityProblem.Code.USER_DISABLED);
        } catch (AuthenticationException exception) {
            auditLog.record(loginAudit(null, AuditEntry.Outcome.DENIED));
            throw IdentityProblem.of(IdentityProblem.Code.INVALID_CREDENTIALS);
        }
        AdminUserView user = ((AdminUserDetails) authentication.getPrincipal()).user();
        sessionEstablisher.establish(user, null, null, 0L, sessionExpiry(), request, response);
        identity.recordLogin(user.id());
        auditLog.record(loginAudit(user.id().toString(), AuditEntry.Outcome.SUCCESS));
        return SessionResponse.from(user);
    }

    /**
     * 退出登录并立即失效服务端会话。
     *
     * @param request 当前请求
     * @return 无正文响应
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /**
     * 读取当前会话主体。
     *
     * @param principal 已认证的会话主体
     * @return 会话响应
     */
    @GetMapping("/me")
    public SessionResponse me(@AuthenticationPrincipal AdminSessionPrincipal session) {
        return new SessionResponse(
                new SessionUser(
                        session.userId().toString(),
                        session.email(),
                        session.displayName(),
                        "ACTIVE"
                ),
                session.roles(),
                session.permissions()
        );
    }

    /**
     * 列出代码内置的权限目录，供后台渲染权限选择器。
     *
     * @return 权限定义
     */
    @GetMapping("/permissions")
    public List<PermissionResponse> permissions() {
        return PermissionCatalog.definitions().stream()
                .map(definition -> new PermissionResponse(
                        definition.code(),
                        definition.module(),
                        definition.name()
                ))
                .toList();
    }

    private Instant sessionExpiry() {
        return Instant.now(clock).plus(properties.sessionTtl());
    }

    private static AuditEntry loginAudit(String actorId, AuditEntry.Outcome outcome) {
        return new AuditEntry(
                AuditEntry.ActorType.USER,
                actorId,
                "user.login",
                "user",
                actorId,
                null,
                outcome
        );
    }

    /**
     * CSRF 令牌响应。
     *
     * @param headerName 需要回传的请求头名称
     * @param token 令牌值
     */
    public record CsrfResponse(String headerName, String token) {
    }

    /**
     * 会话中的用户信息。
     *
     * @param id 账号标识
     * @param email 登录邮箱
     * @param displayName 展示名称
     * @param status 账号状态
     */
    public record SessionUser(String id, String email, String displayName, String status) {
    }

    /**
     * 会话响应。
     *
     * @param user 用户信息
     * @param roles 角色码
     * @param permissions 权限码
     */
    public record SessionResponse(
            SessionUser user,
            java.util.Set<String> roles,
            java.util.Set<String> permissions
    ) {

        /**
         * 由账号视图构造响应。
         *
         * @param user 账号视图
         * @return 会话响应
         */
        public static SessionResponse from(AdminUserView user) {
            return new SessionResponse(
                    new SessionUser(
                            user.id().toString(),
                            user.email(),
                            user.displayName(),
                            user.status().name()
                    ),
                    user.roles(),
                    user.permissions()
            );
        }
    }

    /**
     * 注册请求。
     *
     * @param email 登录邮箱
     * @param displayName 展示名称
     * @param password 明文口令
     */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String displayName,
            @NotBlank @Size(min = 12, max = 200) String password
    ) {
    }

    /**
     * 登录请求。
     *
     * @param email 登录邮箱
     * @param password 明文口令
     */
    public record LoginRequest(
            @NotBlank @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String password
    ) {
    }

    /**
     * 权限目录条目。
     *
     * @param code 权限码
     * @param module 所属模块
     * @param name 中文名称
     */
    public record PermissionResponse(String code, String module, String name) {
    }
}
