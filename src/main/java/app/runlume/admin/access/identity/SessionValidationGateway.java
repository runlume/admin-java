package app.runlume.admin.access.identity;

import java.util.UUID;

/**
 * 向平台确认平台用户是否仍是目标实例有效成员的出站端口。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:35
 */
public interface SessionValidationGateway {

    /**
     * 校验单个平台用户在当前实例中的有效性。
     *
     * <p>实例与 Account 归属只来自本地会话与部署配置，调用方不能提交或覆盖。</p>
     *
     * @param platformAccountId     平台 Account 标识
     * @param platformAppInstanceId 平台 AppInstance 标识
     * @param platformUserId        目标平台用户
     * @return 平台给出的会话状态
     * @throws IdentityProblem 平台不可用、拒绝或响应不符合契约
     */
    SessionState validate(
            UUID platformAccountId,
            UUID platformAppInstanceId,
            UUID platformUserId
    );
}
