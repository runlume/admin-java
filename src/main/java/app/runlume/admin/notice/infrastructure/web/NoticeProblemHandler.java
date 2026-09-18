package app.runlume.admin.notice.infrastructure.web;

import app.runlume.admin.notice.NoticeProblem;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Locale;

/**
 * 把公告失败转换为稳定的 {@code application/problem+json}。
 *
 * <p>业务域自带 Problem 处理，不依赖 {@code access} 的 Web 层实现。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:20
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NoticeProblemHandler {

    /**
     * 转换公告失败。
     *
     * @param problem 公告失败
     * @return 问题响应
     */
    @ExceptionHandler(NoticeProblem.class)
    public ResponseEntity<ProblemDetail> handle(NoticeProblem failure) {
        String code = failure.code().name();
        HttpStatus status = switch (failure.code()) {
            case WORKSPACE_REQUIRED -> HttpStatus.FORBIDDEN;
            case NOTICE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOTICE_STATE_INVALID -> HttpStatus.CONFLICT;
        };
        String detail = switch (failure.code()) {
            case WORKSPACE_REQUIRED -> "当前会话没有工作区，不能访问业务数据";
            case NOTICE_NOT_FOUND -> "公告不存在";
            case NOTICE_STATE_INVALID -> "当前公告状态不允许该操作";
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(
                "urn:runlume:admin:" + code.toLowerCase(Locale.ROOT).replace('_', '-')
        ));
        problem.setTitle(code);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
