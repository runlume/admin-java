package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.identity.IdentityProblem;
import app.runlume.admin.access.identity.SessionState;
import app.runlume.admin.access.identity.SessionValidationGateway;
import app.runlume.platform.sdk.identity.SessionValidationClient;
import app.runlume.platform.sdk.identity.RuntimeIdentityClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 使用平台集成 SDK 校验成员会话。
 *
 * <p>实例服务令牌、Scope 与响应解析都由 SDK 的会话校验客户端负责；本适配器只把结果映射为
 * 本地不可变投影，并把平台失败收敛成稳定错误码。平台不可达或响应不符合契约时失败关闭。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:35
 */
@Component
@ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
public class SdkSessionValidationGateway implements SessionValidationGateway {

    private final SessionValidationClient client;

    /**
     * 创建 SDK 会话校验适配器。
     *
     * @param client 会话校验客户端
     */
    public SdkSessionValidationGateway(SessionValidationClient client) {
        this.client = client;
    }

    @Override
    public SessionState validate(
            UUID platformAccountId,
            UUID platformAppInstanceId,
        UUID platformUserId
    ) {
        try {
            return toSessionState(client.validate(
                    platformAccountId,
                    platformAppInstanceId,
                    platformUserId
            ));
        } catch (IdentityProblem problem) {
            throw problem;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private static SessionState toSessionState(RuntimeIdentityClient.SessionState state) {
        return new SessionState(
                state.platformUserId(),
                state.active(),
                Set.copyOf(state.roles()),
                state.membershipRevision()
        );
    }

    private static IdentityProblem unavailable() {
        return IdentityProblem.of(IdentityProblem.Code.PLATFORM_UNAVAILABLE);
    }
}
