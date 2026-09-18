package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.WorkspaceDirectory;
import app.runlume.admin.access.WorkspaceStatus;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.platform.starter.session.PlatformSessionContext;
import app.runlume.platform.starter.session.SessionContextResolver;
import app.runlume.platform.starter.session.SessionWorkspaceLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.UUID;

/**
 * 把本系统的会话主体与工作区读取接入平台集成 Starter 的会话有效性管线。
 *
 * <p>主体适配只读会话里的派生身份，不接受任何请求参数；平台会话按完整平台边界复验工作区，
 * 本地工作区会话按工作区标识复验状态，两者都不依赖请求参数，与接入标准要求的
 * "每请求复验工作区"一致。</p>
 *
 * <p>本配置不跟随 {@code admin.platform.enabled}：平台关闭时没有平台会话，但本地工作区会话
 * 仍然需要管线做绝对过期与工作区状态校验。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 19:30
 */
@Configuration(proxyBeanMethods = false)
public class SessionPipelineAdapters {

    /**
     * 会话主体适配：平台会话带完整平台边界，本地账号带自有工作区，自举会话不绑定工作区。
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
     * 工作区读取适配：平台会话按 Account 与 AppInstance 读取，本地工作区会话按标识读取。
     *
     * @param workspaces 工作区读取入口
     * @return 工作区读取适配器
     */
    @Bean
    SessionWorkspaceLookup sessionWorkspaceLookup(WorkspaceDirectory workspaces) {
        return new SessionWorkspaceLookup() {

            @Override
            public Optional<WorkspaceSnapshot> find(UUID accountId, UUID appInstanceId) {
                return workspaces
                        .find(accountId, appInstanceId)
                        .map(workspace -> new WorkspaceSnapshot(
                                workspace.id(),
                                workspace.status() == WorkspaceStatus.ACTIVE
                        ));
            }

            @Override
            public Optional<WorkspaceSnapshot> findById(UUID workspaceId) {
                return workspaces
                        .statusOf(workspaceId)
                        .map(status -> new WorkspaceSnapshot(
                                workspaceId,
                                status == WorkspaceStatus.ACTIVE
                        ));
            }
        };
    }
}
