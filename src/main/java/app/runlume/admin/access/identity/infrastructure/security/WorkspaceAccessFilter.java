package app.runlume.admin.access.identity.infrastructure.security;

import app.runlume.admin.access.WorkspaceDirectory;
import app.runlume.admin.access.WorkspaceStatus;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每个请求重新确认会话所属工作区仍然存在且为 {@code ACTIVE}。
 *
 * <p>平台暂停或注销实例只改工作区状态，不会回收已经建立的浏览器会话。本过滤器把状态变化
 * 收敛到下一个请求：平台边界与数据库不一致、工作区不存在或状态不再为 {@code ACTIVE} 时，
 * 立即失效服务端会话并返回 {@code 401}，由前端重新回到平台入口。本地自有账号没有工作区边界，
 * 其租户数据访问限制由业务域入口单独拒绝。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:02
 */
public class WorkspaceAccessFilter extends OncePerRequestFilter {

    private final WorkspaceDirectory workspaces;

    /**
     * 创建工作区复验过滤器。
     *
     * @param workspaces 工作区读取入口
     */
    public WorkspaceAccessFilter(WorkspaceDirectory workspaces) {
        this.workspaces = workspaces;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AdminSessionPrincipal principal
                && !active(principal)) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean active(AdminSessionPrincipal principal) {
        if (principal.workspaceId() == null) {
            return true;
        }
        return workspaces
                .find(principal.platformAccountId(), principal.platformAppInstanceId())
                .filter(workspace -> workspace.id().equals(principal.workspaceId()))
                .filter(workspace -> workspace.status() == WorkspaceStatus.ACTIVE)
                .isPresent();
    }
}
