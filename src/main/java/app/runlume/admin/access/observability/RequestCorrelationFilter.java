package app.runlume.admin.access.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为每个请求建立服务端关联标识，并写入响应头。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:40
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = RequestCorrelation.resolve(
                request.getHeader(RequestCorrelation.HEADER)
        );
        request.setAttribute(RequestCorrelation.HEADER, correlationId);
        response.setHeader(RequestCorrelation.HEADER, correlationId);
        filterChain.doFilter(request, response);
    }
}
