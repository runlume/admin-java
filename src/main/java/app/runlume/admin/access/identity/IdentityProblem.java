package app.runlume.admin.access.identity;

import java.io.Serial;
import java.util.Objects;

/**
 * 身份边界内可安全外传的稳定失败。
 *
 * <p>异常正文只携带稳定错误码，不携带口令、Token、邮箱是否存在之外的信息或上游响应。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:50
 */
public final class IdentityProblem extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Code code;

    private IdentityProblem(Code code) {
        super(code.name());
        this.code = code;
    }

    /**
     * 创建指定错误码的身份失败。
     *
     * @param code 稳定错误码
     * @return 身份失败
     */
    public static IdentityProblem of(Code code) {
        return new IdentityProblem(Objects.requireNonNull(code, "code"));
    }

    /**
     * 返回稳定错误码。
     *
     * @return 稳定错误码
     */
    public Code code() {
        return code;
    }

    /**
     * 身份失败分类。
     */
    public enum Code {
        /** 邮箱已被占用。 */
        EMAIL_ALREADY_REGISTERED,
        /** 邮箱或口令不正确。 */
        INVALID_CREDENTIALS,
        /** 账号被禁用。 */
        USER_DISABLED,
        /** 账号不存在。 */
        USER_NOT_FOUND,
        /** 引用了不存在的角色。 */
        ROLE_NOT_FOUND,
        /** 平台明确拒绝了本次 Launch Code。 */
        LAUNCH_REJECTED,
        /** 平台暂时不可用。 */
        LAUNCH_UNAVAILABLE,
        /** 模块服务身份不可用。 */
        PLATFORM_UNAVAILABLE,
        /** 部署未开放自助注册。 */
        REGISTRATION_DISABLED
    }
}
