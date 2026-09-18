package app.runlume.admin.access.observability;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 服务端请求关联标识。
 *
 * <p>入站 {@code X-Request-Id} 只用于把同一链路的日志串起来，必须通过严格格式校验才被
 * 采纳，且绝不作为身份、租户或授权依据；不合法时由服务端生成。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:40
 */
public final class RequestCorrelation {

    /**
     * 请求与响应头名称。
     */
    public static final String HEADER = "X-Request-Id";

    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9._:-]{8,120}$");

    private RequestCorrelation() {
    }

    /**
     * 采纳合法的入站关联标识，否则生成新的服务端标识。
     *
     * @param candidate 入站请求头，可为 null
     * @return 可安全写入日志与响应的关联标识
     */
    public static String resolve(String candidate) {
        if (candidate != null && SAFE_VALUE.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 读取当前请求已解析的关联标识。
     *
     * @param request 当前请求
     * @return 关联标识；过滤器尚未执行时为空
     */
    public static Optional<String> current(HttpServletRequest request) {
        Object value = request.getAttribute(HEADER);
        return value instanceof String text ? Optional.of(text) : Optional.empty();
    }
}
