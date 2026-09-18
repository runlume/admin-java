package app.runlume.admin.access.identity.infrastructure.security;

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
import java.time.Clock;
import java.time.Instant;

/**
 * 强制会话绝对过期。
 *
 * <p>Spring Session 只保证空闲超时；平台 Launch 建立的会话必须以 Context Token 的
 * {@code exp} 为硬上限，因此每个请求都再次核对绝对过期时间。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:20
 */
public class SessionAbsoluteExpiryFilter extends OncePerRequestFilter {

    private final Clock clock;

    /**
     * 创建绝对过期过滤器。
     *
     * @param clock 判定时间源
     */
    public SessionAbsoluteExpiryFilter(Clock clock) {
        this.clock = clock;
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
                && principal.expiredAt(Instant.now(clock))) {
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
}
