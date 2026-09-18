package app.runlume.admin.access.identity.infrastructure.security;

import app.runlume.admin.access.WorkspaceView;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.admin.access.identity.AdminUserView;
import app.runlume.admin.access.identity.PermissionCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 建立服务端本地会话。
 *
 * <p>登录与 Launch 都通过本组件写入会话，保证两条链路的行为一致：先生成新的会话标识以
 * 防御会话固定，再把只含派生身份的会话主体交给 Spring Security 保存到服务端 Session。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:40
 */
@Component
public class LocalSessionEstablisher {

    private final SecurityContextRepository securityContextRepository;

    /**
     * 创建会话建立器。
     *
     * @param securityContextRepository 会话上下文仓库
     */
    public LocalSessionEstablisher(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * 为本地账号建立会话；账号已绑定工作区时把工作区写入主体。
     *
     * <p>本地工作区会话没有平台边界，因此不参与平台成员校验，租户隔离由工作区过滤保证。</p>
     *
     * @param user 账号视图
     * @param localWorkspaceId 账号绑定的本地工作区；未绑定时传 null
     * @param expiresAt 会话绝对过期时间
     * @param request 当前请求
     * @param response 当前响应
     */
    public void establish(
            AdminUserView user,
            UUID localWorkspaceId,
            Instant expiresAt,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        establish(user, null, null, localWorkspaceId, 0L, expiresAt, request, response);
    }

    /**
     * 为账号建立新的本地会话。
     *
     * @param user 账号视图
     * @param workspace 会话绑定的工作区；本地自有账号传 null
     * @param platformUserId 平台用户标识；本地自有账号传 null
     * @param membershipRevision 平台成员授权修订号；本地自有账号传 0
     * @param expiresAt 会话绝对过期时间
     * @param request 当前请求
     * @param response 当前响应
     */
    public void establish(
            AdminUserView user,
            WorkspaceView workspace,
            UUID platformUserId,
            long membershipRevision,
            Instant expiresAt,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        establish(
                user,
                workspace,
                platformUserId,
                null,
                membershipRevision,
                expiresAt,
                request,
                response
        );
    }

    /**
     * 建立会话的公共实现。
     *
     * @param user 账号视图
     * @param workspace 平台映射的工作区；本地会话为空
     * @param platformUserId 平台用户标识；本地会话为空
     * @param localWorkspaceId 本地账号绑定的工作区；平台会话为空
     * @param membershipRevision 平台成员授权修订号；本地会话为 0
     * @param expiresAt 会话绝对过期时间
     * @param request 当前请求
     * @param response 当前响应
     */
    private void establish(
            AdminUserView user,
            WorkspaceView workspace,
            UUID platformUserId,
            UUID localWorkspaceId,
            long membershipRevision,
            Instant expiresAt,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AdminSessionPrincipal principal = new AdminSessionPrincipal(
                user.id(),
                platformUserId,
                workspace == null ? localWorkspaceId : workspace.id(),
                workspace == null ? null : workspace.platformAccountId(),
                workspace == null ? null : workspace.platformAppInstanceId(),
                user.email(),
                user.displayName(),
                user.roles(),
                user.permissions(),
                membershipRevision,
                expiresAt
        );
        List<SimpleGrantedAuthority> authorities = PermissionCatalog
                .expand(user.permissions())
                .stream()
                .sorted()
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                authorities
        ));
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        request.changeSessionId();
        securityContextRepository.saveContext(context, request, response);
    }
}
