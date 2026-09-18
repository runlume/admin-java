package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.identity.IdentityProblem;
import app.runlume.admin.access.identity.PlatformLaunchGateway;
import app.runlume.admin.access.identity.PlatformLaunchIdentity;
import app.runlume.platform.sdk.identity.PlatformLaunchClient;
import app.runlume.platform.sdk.identity.RuntimeIdentityClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 使用平台集成 SDK 交换一次性 Launch Code。
 *
 * <p>SDK 负责申请模块服务令牌、调用平台、验证 {@code iss/aud/kid/签名/exp} 与模块边界，并把
 * 结果收敛为不含原始 Token 的最小投影。本适配器只把 SDK 的失败分类映射成本系统错误码。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:30
 */
@Component
@ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
public class SdkPlatformLaunchGateway implements PlatformLaunchGateway {

    private final PlatformLaunchClient client;

    /**
     * 创建 SDK Launch 适配器。
     *
     * @param client Launch 交换客户端
     */
    public SdkPlatformLaunchGateway(PlatformLaunchClient client) {
        this.client = client;
    }

    @Override
    public PlatformLaunchIdentity exchange(String launchCode) {
        try {
            RuntimeIdentityClient.VerifiedLaunch verified = client.exchange(launchCode);
            return new PlatformLaunchIdentity(
                    verified.platformUserId(),
                    verified.platformAccountId(),
                    verified.platformAppInstanceId(),
                    verified.displayName(),
                    verified.email(),
                    verified.roles(),
                    verified.membershipRevision(),
                    verified.expiresAt(),
                    verified.actionPath()
            );
        } catch (RuntimeIdentityClient.LaunchException exception) {
            throw switch (exception.reason()) {
                case INVALID, USED, EXPIRED, MODULE_MISMATCH ->
                        IdentityProblem.of(IdentityProblem.Code.LAUNCH_REJECTED);
                case UNAVAILABLE ->
                        IdentityProblem.of(IdentityProblem.Code.LAUNCH_UNAVAILABLE);
            };
        } catch (RuntimeException exception) {
            throw IdentityProblem.of(IdentityProblem.Code.LAUNCH_UNAVAILABLE);
        }
    }

}
