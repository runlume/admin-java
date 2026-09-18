package app.runlume.admin.access.infrastructure.web;

import app.runlume.admin.access.WorkspaceProblem;
import app.runlume.admin.access.identity.IdentityProblem;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Locale;

/**
 * 把身份与工作区失败转换为稳定的 {@code application/problem+json}。
 *
 * <p>响应只带稳定 {@code code}、类型 URI 与固定文案，不携带堆栈、上游响应体、Token、
 * 口令或账号是否存在之外的推断信息。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:00
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessProblemHandler {

    private static final String TYPE_PREFIX = "urn:runlume:admin:";

    /**
     * 转换身份失败。
     *
     * @param problem 身份失败
     * @return 问题响应
     */
    @ExceptionHandler(IdentityProblem.class)
    public ResponseEntity<ProblemDetail> handleIdentityProblem(IdentityProblem problem) {
        String code = problem.code().name();
        return problem(statusOf(problem.code()), code, detailOf(problem.code()));
    }

    /**
     * 转换工作区与生命周期失败。
     *
     * @param problem 工作区失败
     * @return 问题响应
     */
    @ExceptionHandler(WorkspaceProblem.class)
    public ResponseEntity<ProblemDetail> handleWorkspaceProblem(WorkspaceProblem problem) {
        String code = problem.code().name();
        return problem(statusOf(problem.code()), code, detailOf(problem.code()));
    }

    /**
     * 转换请求体校验失败。
     *
     * @param exception 校验异常
     * @return 问题响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求参数不合法");
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }

    /**
     * 转换鉴权失败。
     *
     * @param exception 鉴权异常
     * @return 问题响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "当前账号没有执行该操作的权限");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(code);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }

    private static HttpStatus statusOf(IdentityProblem.Code code) {
        return switch (code) {
            case INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case USER_DISABLED, REGISTRATION_DISABLED -> HttpStatus.FORBIDDEN;
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case EMAIL_ALREADY_REGISTERED -> HttpStatus.CONFLICT;
            case ROLE_NOT_FOUND, LAUNCH_REJECTED -> HttpStatus.BAD_REQUEST;
            case LAUNCH_UNAVAILABLE, PLATFORM_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static HttpStatus statusOf(WorkspaceProblem.Code code) {
        return switch (code) {
            case WORKSPACE_NOT_FOUND, OPERATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case IDEMPOTENCY_CONFLICT, APP_INSTANCE_ALREADY_REGISTERED -> HttpStatus.CONFLICT;
            case WORKSPACE_NOT_ACTIVE -> HttpStatus.FORBIDDEN;
        };
    }

    private static String detailOf(IdentityProblem.Code code) {
        return switch (code) {
            case EMAIL_ALREADY_REGISTERED -> "该邮箱已注册";
            case INVALID_CREDENTIALS -> "邮箱或口令不正确";
            case USER_DISABLED -> "账号已被禁用";
            case USER_NOT_FOUND -> "账号不存在";
            case ROLE_NOT_FOUND -> "指定的角色不存在";
            case LAUNCH_REJECTED -> "平台拒绝本次访问，请从平台重新进入";
            case LAUNCH_UNAVAILABLE -> "平台暂时不可用，请稍后重试";
            case PLATFORM_UNAVAILABLE -> "平台服务身份不可用，请检查配置";
            case REGISTRATION_DISABLED -> "本部署未开放自助注册";
        };
    }

    private static String detailOf(WorkspaceProblem.Code code) {
        return switch (code) {
            case WORKSPACE_NOT_FOUND -> "实例工作区不存在";
            case IDEMPOTENCY_CONFLICT -> "相同幂等键绑定了不同的请求内容";
            case APP_INSTANCE_ALREADY_REGISTERED -> "平台实例已被其它账号或模块占用";
            case OPERATION_NOT_FOUND -> "生命周期操作不存在";
            case WORKSPACE_NOT_ACTIVE -> "实例已暂停或注销，无法建立会话";
        };
    }
}
