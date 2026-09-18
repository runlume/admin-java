package app.runlume.admin.access.identity;

import app.runlume.admin.access.WorkspaceLifecycle;
import app.runlume.admin.access.WorkspaceDirectory;
import app.runlume.admin.access.WorkspaceView;
import app.runlume.admin.support.PostgresTestSupport;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static app.runlume.admin.access.identity.infrastructure.jooq.tables.AdminUser.ADMIN_USER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台成员必须绑定工作区，本地运营账号必须没有工作区。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 16:10
 */
@Transactional
class PlatformMemberScopeTests extends PostgresTestSupport {

    @Autowired
    private AdminIdentity identity;

    @Autowired
    private WorkspaceLifecycle lifecycle;

    @Autowired
    private WorkspaceDirectory workspaces;

    @Autowired
    private DSLContext dsl;

    @Test
    void platformMemberIsBoundToItsWorkspace() {
        UUID accountId = UUID.randomUUID();
        UUID appInstanceId = UUID.randomUUID();
        lifecycle.provision(
                new WorkspaceLifecycle.ProvisionRequest(
                        accountId,
                        appInstanceId,
                        "example.admin",
                        "1.0.0"
                ),
                "platform-member-" + UUID.randomUUID()
        );
        WorkspaceView workspace = workspaces.find(accountId, appInstanceId).orElseThrow();

        AdminUserView member = identity.upsertPlatformUser(
                new PlatformLaunchIdentity(
                        UUID.randomUUID(),
                        accountId,
                        appInstanceId,
                        "平台成员",
                        "member@runlume.local",
                        List.of("SAAS_MEMBER"),
                        3L,
                        Instant.now().plusSeconds(300),
                        null
                ),
                workspace.id()
        );

        assertThat(storedWorkspaceId(member.id())).isEqualTo(workspace.id());
    }

    @Test
    void localAccountHasNoWorkspace() {
        AdminUserView local = identity.register(
                "local-" + UUID.randomUUID() + "@runlume.local",
                "本地运营",
                "runlume-password"
        );

        assertThat(storedWorkspaceId(local.id())).isNull();
    }

    @Test
    void instanceAdminRoleIsDerivedAndRevokedWithMembership() {
        UUID accountId = UUID.randomUUID();
        UUID appInstanceId = UUID.randomUUID();
        UUID platformUserId = UUID.randomUUID();
        WorkspaceView workspace = provisionWorkspace(accountId, appInstanceId);

        AdminUserView member = launch(
                accountId,
                appInstanceId,
                workspace.id(),
                platformUserId,
                List.of("SAAS_MEMBER")
        );
        assertThat(member.roles()).containsExactly("member");

        AdminUserView admin = launch(
                accountId,
                appInstanceId,
                workspace.id(),
                platformUserId,
                List.of("SAAS_ADMIN")
        );
        assertThat(admin.roles()).contains("admin");

        AdminUserView revoked = launch(
                accountId,
                appInstanceId,
                workspace.id(),
                platformUserId,
                List.of("SAAS_MEMBER")
        );
        assertThat(revoked.roles()).containsExactly("member");
    }

    @Test
    void localRoleChangeCannotRemoveDerivedAdministrator() {
        UUID accountId = UUID.randomUUID();
        UUID appInstanceId = UUID.randomUUID();
        WorkspaceView workspace = provisionWorkspace(accountId, appInstanceId);
        AdminUserView admin = launch(
                accountId,
                appInstanceId,
                workspace.id(),
                UUID.randomUUID(),
                List.of("SAAS_ADMIN")
        );

        AdminUserView updated = identity.changeRoles(
                workspace.id(),
                admin.id(),
                java.util.Set.of("member")
        );

        assertThat(updated.roles()).contains("admin");
    }

    @Test
    void memberListingIsScopedToWorkspace() {
        UUID otherAccountId = UUID.randomUUID();
        UUID otherAppInstanceId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID appInstanceId = UUID.randomUUID();
        WorkspaceView other = provisionWorkspace(otherAccountId, otherAppInstanceId);
        WorkspaceView workspace = provisionWorkspace(accountId, appInstanceId);
        launch(
                otherAccountId,
                otherAppInstanceId,
                other.id(),
                UUID.randomUUID(),
                List.of("SAAS_MEMBER")
        );
        AdminUserView mine = launch(
                accountId,
                appInstanceId,
                workspace.id(),
                UUID.randomUUID(),
                List.of("SAAS_MEMBER")
        );

        List<AdminUserView> listed = identity.listUsers(workspace.id(), 0, 20, null);

        assertThat(listed).extracting(AdminUserView::id).containsExactly(mine.id());
        assertThat(identity.countUsers(workspace.id(), null)).isEqualTo(1L);
        assertThat(identity.findUser(workspace.id(), mine.id())).isPresent();
    }

    private WorkspaceView provisionWorkspace(UUID accountId, UUID appInstanceId) {
        lifecycle.provision(
                new WorkspaceLifecycle.ProvisionRequest(
                        accountId,
                        appInstanceId,
                        "example.admin",
                        "1.0.0"
                ),
                "platform-member-" + UUID.randomUUID()
        );
        return workspaces.find(accountId, appInstanceId).orElseThrow();
    }

    private AdminUserView launch(
            UUID accountId,
            UUID appInstanceId,
            UUID workspaceId,
            UUID platformUserId,
            List<String> roles
    ) {
        return identity.upsertPlatformUser(
                new PlatformLaunchIdentity(
                        platformUserId,
                        accountId,
                        appInstanceId,
                        "平台成员",
                        null,
                        roles,
                        1L,
                        Instant.now().plusSeconds(300),
                        null
                ),
                workspaceId
        );
    }

    private UUID storedWorkspaceId(UUID userId) {
        return dsl.select(ADMIN_USER.WORKSPACE_ID)
                .from(ADMIN_USER)
                .where(ADMIN_USER.ID.eq(userId))
                .fetchOne(ADMIN_USER.WORKSPACE_ID);
    }

}
