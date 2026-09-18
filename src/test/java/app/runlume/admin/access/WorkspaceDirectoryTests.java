package app.runlume.admin.access;

import app.runlume.admin.access.WorkspaceLifecycle.ProvisionRequest;
import app.runlume.admin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工作区映射的按平台边界读取。
 *
 * <p>用例通过真实生命周期开通写入映射并在事务内回滚，避免手工建表或直接改数据。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:02
 */
@Transactional
class WorkspaceDirectoryTests extends PostgresTestSupport {

    @Autowired
    private WorkspaceDirectory workspaces;

    @Autowired
    private WorkspaceLifecycle lifecycle;

    @Test
    void findsProvisionedWorkspaceByPlatformBoundary() {
        UUID accountId = UUID.randomUUID();
        UUID appInstanceId = UUID.randomUUID();
        LifecycleOperationView operation = lifecycle.provision(
                new ProvisionRequest(accountId, appInstanceId, "example.admin", "1.0.0"),
                "workspace-directory-" + UUID.randomUUID()
        );

        WorkspaceView found = workspaces.find(accountId, appInstanceId).orElseThrow();

        assertThat(found.status()).isEqualTo(WorkspaceStatus.ACTIVE);
        assertThat(found.platformAccountId()).isEqualTo(accountId);
        assertThat(found.externalInstanceId()).isEqualTo(operation.externalInstanceId());
    }

    @Test
    void returnsEmptyWhenPlatformBoundaryIsUnknown() {
        assertThat(workspaces.find(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
    }
}
