package app.runlume.admin.access.identity.infrastructure.security;

import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.admin.access.identity.SessionState;
import app.runlume.admin.access.identity.infrastructure.SessionValidationCache;
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
 * 每个请求按声明窗口向平台确认平台成员仍然有效。
 *
 * <p>工作区状态由 {@code WorkspaceAccessFilter} 复验，本过滤器只处理成员与 Account 层的撤销：
 * 平台判定不活跃、平台不可达或响应异常时都立即失效会话并返回 {@code 401}，不把失败静默放行。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:35
 */
public class SessionValidationFilter extends OncePerRequestFilter {

    private final SessionValidationCache validations;

    /**
     * 创建成员校验过滤器。
     *
     * @param validations 会话校验缓存
     */
    public SessionValidationFilter(SessionValidationCache validations) {
        this.validations = validations;
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
                && principal.workspaceId() != null
                && !active(principal, writeRequest(request))) {
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

    private boolean active(AdminSessionPrincipal principal, boolean writeRequest) {
        try {
            SessionState state = validations.validate(principal, writeRequest);
            return state.active();
        } catch (RuntimeException exception) {
            // 安全边界失败关闭：平台不可达或响应异常时不继续使用既有会话。
            return false;
        }
    }

    private static boolean writeRequest(HttpServletRequest request) {
        String method = request.getMethod();
        return !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method));
    }
}
