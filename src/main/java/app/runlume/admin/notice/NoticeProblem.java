package app.runlume.admin.notice;

import java.io.Serial;
import java.util.Objects;

/**
 * 公告业务域的稳定失败。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:20
 */
public final class NoticeProblem extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Code code;

    private NoticeProblem(Code code) {
        super(code.name());
        this.code = code;
    }

    /**
     * 创建指定错误码的公告失败。
     *
     * @param code 稳定错误码
     * @return 公告失败
     */
    public static NoticeProblem of(Code code) {
        return new NoticeProblem(Objects.requireNonNull(code, "code"));
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
     * 公告失败分类。
     */
    public enum Code {
        /** 当前会话没有工作区，租户业务域拒绝读写。 */
        WORKSPACE_REQUIRED,
        /** 公告不存在或不在当前工作区可见范围内。 */
        NOTICE_NOT_FOUND,
        /** 公告状态不允许该操作。 */
        NOTICE_STATE_INVALID
    }
}
