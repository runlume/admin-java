package app.runlume.admin.access;

import java.io.Serial;
import java.util.Objects;

/**
 * 工作区与生命周期边界的稳定失败。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:00
 */
public final class WorkspaceProblem extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Code code;

    private WorkspaceProblem(Code code) {
        super(code.name());
        this.code = code;
    }

    /**
     * 创建指定错误码的工作区失败。
     *
     * @param code 稳定错误码
     * @return 工作区失败
     */
    public static WorkspaceProblem of(Code code) {
        return new WorkspaceProblem(Objects.requireNonNull(code, "code"));
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
     * 工作区失败分类。
     */
    public enum Code {
        /** 目标 workspace 不存在。 */
        WORKSPACE_NOT_FOUND,
        /** 相同幂等键绑定了不同请求摘要。 */
        IDEMPOTENCY_CONFLICT,
        /** 平台实例标识已被其它 Account 或模块占用。 */
        APP_INSTANCE_ALREADY_REGISTERED,
        /** 生命周期操作不存在。 */
        OPERATION_NOT_FOUND,
        /** workspace 已暂停或注销，拒绝建立新会话。 */
        WORKSPACE_NOT_ACTIVE
    }
}
