package app.runlume.admin.notice;

import app.runlume.admin.access.WorkspaceDirectory;
import app.runlume.admin.access.WorkspaceLifecycle;
import app.runlume.admin.access.WorkspaceView;
import app.runlume.admin.access.identity.AdminIdentity;
import app.runlume.admin.access.identity.AdminUserView;
import app.runlume.admin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 业务数据只属于工作区：没有工作区的会话被拒绝，跨工作区互不可见。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 16:40
 */
@Transactional
class NoticeScopeTests extends PostgresTestSupport {

    @Autowired
    private NoticeDirectory notices;

    @Autowired
    private WorkspaceLifecycle lifecycle;

    @Autowired
    private WorkspaceDirectory workspaces;

    @Autowired
    private AdminIdentity identity;

    @Test
    void sessionWithoutWorkspaceIsRejected() {
        assertThatThrownBy(() -> notices.list(0, 20, null, null))
                .isInstanceOf(NoticeProblem.class)
                .extracting(problem -> ((NoticeProblem) problem).code())
                .isEqualTo(NoticeProblem.Code.WORKSPACE_REQUIRED);
        assertThatThrownBy(() -> notices.create("标题", "正文", null, authorId()))
                .isInstanceOf(NoticeProblem.class)
                .extracting(problem -> ((NoticeProblem) problem).code())
                .isEqualTo(NoticeProblem.Code.WORKSPACE_REQUIRED);
    }

    @Test
    void noticesAreVisibleOnlyInsideTheirWorkspace() {
        WorkspaceView mine = provisionWorkspace();
        WorkspaceView other = provisionWorkspace();
        Notice created = notices.create("工作区公告", "正文", mine.id(), authorId());

        assertThat(notices.list(0, 20, null, mine.id()))
                .extracting(Notice::id)
                .containsExactly(created.id());
        assertThat(notices.list(0, 20, null, other.id())).isEmpty();
        assertThat(notices.find(created.id(), other.id())).isEmpty();
        assertThat(notices.find(created.id(), mine.id())).isPresent();
    }

    private UUID authorId() {
        AdminUserView author = identity.register(
                "notice-" + UUID.randomUUID() + "@runlume.local",
                "公告作者",
                "runlume-password"
        );
        return author.id();
    }

    private WorkspaceView provisionWorkspace() {
        UUID accountId = UUID.randomUUID();
        UUID appInstanceId = UUID.randomUUID();
        lifecycle.provision(
                new WorkspaceLifecycle.ProvisionRequest(
                        accountId,
                        appInstanceId,
                        "example.admin",
                        "1.0.0"
                ),
                "notice-scope-" + UUID.randomUUID()
        );
        return workspaces.find(accountId, appInstanceId).orElseThrow();
    }
}
