package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.WorkspaceDirectory;
import app.runlume.admin.access.WorkspaceStatus;
import app.runlume.admin.access.WorkspaceView;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.platform.starter.session.PlatformSessionContext;
import app.runlume.platform.starter.session.SessionWorkspaceLookup;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话边界与工作区读取两个适配端口的行为。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 19:40
 */
class PlatformSessionAdaptersTests {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID APP_INSTANCE_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PLATFORM_USER_ID = UUID.randomUUID();
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-18T20:00:00Z");

    private final PlatformSessionAdapters adapters = new PlatformSessionAdapters();

    @Test
    void mapsPlatformSessionAndSkipsForeignPrincipals() {
        PlatformSessionContext context = adapters.sessionContextResolver().resolve(
                platformPrincipal()
        );

        assertThat(context.workspaceBound()).isTrue();
        assertThat(context.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(context.platformUserId()).isEqualTo(PLATFORM_USER_ID);
        assertThat(context.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(adapters.sessionContextResolver().resolve("anonymous")).isNull();
    }

    @Test
    void reportsWorkspaceActivityFromDirectory() {
        SessionWorkspaceLookup active = adapters.sessionWorkspaceLookup(
                directoryWith(WorkspaceStatus.ACTIVE)
        );
        SessionWorkspaceLookup suspended = adapters.sessionWorkspaceLookup(
                directoryWith(WorkspaceStatus.SUSPENDED)
        );

        assertThat(active.find(ACCOUNT_ID, APP_INSTANCE_ID).orElseThrow().active()).isTrue();
        assertThat(suspended.find(ACCOUNT_ID, APP_INSTANCE_ID).orElseThrow().active()).isFalse();
        assertThat(adapters.sessionWorkspaceLookup(new StubDirectory(null))
                .find(ACCOUNT_ID, APP_INSTANCE_ID)).isEmpty();
    }

    private static WorkspaceDirectory directoryWith(WorkspaceStatus status) {
        return new StubDirectory(new WorkspaceView(
                WORKSPACE_ID,
                ACCOUNT_ID,
                APP_INSTANCE_ID,
                "example.admin",
                "1.0.0",
                "ws_test",
                status,
                EXPIRES_AT,
                EXPIRES_AT
        ));
    }

    /**
     * 只实现本用例需要的工作区读取替身。
     */
    private static final class StubDirectory implements WorkspaceDirectory {

        private final WorkspaceView workspace;

        private StubDirectory(WorkspaceView workspace) {
            this.workspace = workspace;
        }

        @Override
        public Optional<WorkspaceView> findByAppInstance(UUID appInstanceId) {
            return Optional.ofNullable(workspace);
        }

        @Override
        public Optional<WorkspaceView> find(UUID accountId, UUID appInstanceId) {
            return Optional.ofNullable(workspace);
        }

        @Override
        public Optional<WorkspaceView> findByExternalInstanceId(String externalInstanceId) {
            return Optional.empty();
        }

        @Override
        public java.util.List<WorkspaceView> list(int offset, int limit) {
            return java.util.List.of();
        }

        @Override
        public long count() {
            return 0L;
        }
    }

    private static AdminSessionPrincipal platformPrincipal() {
        return new AdminSessionPrincipal(
                UUID.randomUUID(),
                PLATFORM_USER_ID,
                WORKSPACE_ID,
                ACCOUNT_ID,
                APP_INSTANCE_ID,
                "member@runlume.local",
                "平台成员",
                Set.of("member"),
                Set.of("example.admin.notice.view"),
                3L,
                EXPIRES_AT
        );
    }
}
