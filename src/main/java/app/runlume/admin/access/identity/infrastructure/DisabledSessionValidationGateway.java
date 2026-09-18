package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.identity.IdentityProblem;
import app.runlume.admin.access.identity.SessionState;
import app.runlume.admin.access.identity.SessionValidationGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 关闭平台接入时的失败关闭实现。
 *
 * <p>平台关闭时本地自有账号没有工作区边界，会话校验不会被执行；本实现只保证依赖存在且
 * 一旦被调用就拒绝，不静默放行。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:35
 */
@Component
@ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledSessionValidationGateway implements SessionValidationGateway {

    @Override
    public SessionState validate(
            UUID platformAccountId,
            UUID platformAppInstanceId,
            UUID platformUserId
    ) {
        throw IdentityProblem.of(IdentityProblem.Code.PLATFORM_UNAVAILABLE);
    }
}
