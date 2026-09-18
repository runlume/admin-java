package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.identity.IdentityProblem;
import app.runlume.admin.access.identity.PlatformLaunchGateway;
import app.runlume.admin.access.identity.PlatformLaunchIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 未启用平台接入时的失败关闭 Launch 入口。
 *
 * <p>保持路由存在并返回稳定失败，避免把“未接入”表现成难以诊断的 404。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:50
 */
@Component
@ConditionalOnProperty(
        name = "admin.platform.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DisabledPlatformLaunchGateway implements PlatformLaunchGateway {

    @Override
    public PlatformLaunchIdentity exchange(String launchCode) {
        throw IdentityProblem.of(IdentityProblem.Code.LAUNCH_UNAVAILABLE);
    }
}
