package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.WorkspaceDirectory;
import app.runlume.admin.access.WorkspaceStatus;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.platform.starter.session.PlatformSessionContext;
import app.runlume.platform.starter.session.SessionContextResolver;
import app.runlume.platform.starter.session.SessionWorkspaceLookup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把本系统的会话主体与工作区映射接入平台集成 Starter 的会话有效性管线。
 *
 * <p>主体适配只读会话里的派生身份，不接受任何请求参数；工作区读取按完整平台边界查询，
 * 与接入标准要求的"每请求复验工作区"一致。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 19:30
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "admin.platform.enabled", havingValue = "true")
public class PlatformSessionAdapters {

    /**
     * 会话主体适配：本地运营会话返回不绑定工作区的上下文，交由管线跳过。
     *
     * @return 主体适配器
     */
    @Bean
    SessionContextResolver sessionContextResolver() {
        return principal -> {
            if (principal instanceof AdminSessionPrincipal session) {
                return new PlatformSessionContext(
                        session.workspaceId(),
                        session.platformAccountId(),
                        session.platformAppInstanceId(),
                        session.platformUserId(),
                        session.expiresAt()
                );
            }
            return null;
        };
    }

    /**
     * 工作区读取适配：按 Account 与 AppInstance 读取本地工作区状态。
     *
     * @param workspaces 工作区读取入口
     * @return 工作区读取适配器
     */
    @Bean
    SessionWorkspaceLookup sessionWorkspaceLookup(WorkspaceDirectory workspaces) {
        return (accountId, appInstanceId) -> workspaces
                .find(accountId, appInstanceId)
                .map(workspace -> new SessionWorkspaceLookup.WorkspaceSnapshot(
                        workspace.id(),
                        workspace.status() == WorkspaceStatus.ACTIVE
                ));
    }
}
