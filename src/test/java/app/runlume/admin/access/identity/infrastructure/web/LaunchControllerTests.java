package app.runlume.admin.access.identity.infrastructure.web;

import app.runlume.admin.access.WorkspaceDirectory;
import app.runlume.admin.access.WorkspaceStatus;
import app.runlume.admin.access.WorkspaceView;
import app.runlume.admin.access.identity.AdminIdentity;
import app.runlume.admin.access.identity.AdminUserView;
import app.runlume.admin.access.identity.LocalAccountProperties;
import app.runlume.admin.access.identity.PlatformLaunchGateway;
import app.runlume.admin.access.identity.PlatformLaunchIdentity;
import app.runlume.admin.access.identity.UserStatus;
import app.runlume.admin.access.identity.infrastructure.security.LocalSessionEstablisher;
import app.runlume.admin.access.observability.AdminAuditLog;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Launch 建立的本地会话不再被 Context Token 的五分钟上限压缩。
 *
 * <p>短票据只证明进入时点；会话寿命取本地配置，撤销时效由会话校验窗口决定。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 17:20
 */
class LaunchControllerTests {

    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");
    private static final Duration SESSION_TTL = Duration.ofHours(12);
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID APP_INSTANCE_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void sessionLifetimeFollowsLocalConfigurationNotContextTokenExpiry() throws Exception {
        PlatformLaunchGateway gateway = mock(PlatformLaunchGateway.class);
        AdminIdentity identity = mock(AdminIdentity.class);
        WorkspaceDirectory workspaces = mock(WorkspaceDirectory.class);
        LocalSessionEstablisher establisher = mock(LocalSessionEstablisher.class);
        AdminAuditLog auditLog = mock(AdminAuditLog.class);
        WorkspaceView workspace = new WorkspaceView(
                WORKSPACE_ID,
                ACCOUNT_ID,
                APP_INSTANCE_ID,
                "example.admin",
                "1.0.0",
                "ws_test",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW
        );
        AdminUserView user = new AdminUserView(
                USER_ID,
                "member@runlume.local",
                "平台成员",
                UserStatus.ACTIVE,
                Set.of("member"),
                Set.of("example.admin.notice.view"),
                NOW,
                NOW,
                null
        );
        when(gateway.exchange("code")).thenReturn(new PlatformLaunchIdentity(
                UUID.randomUUID(),
                ACCOUNT_ID,
                APP_INSTANCE_ID,
                "平台成员",
                null,
                List.of("SAAS_MEMBER"),
                7L,
                NOW.plusSeconds(60),
                null
        ));
        when(workspaces.findByAppInstance(APP_INSTANCE_ID)).thenReturn(Optional.of(workspace));
        when(identity.upsertPlatformUser(any(), eq(WORKSPACE_ID))).thenReturn(user);
        LaunchController controller = new LaunchController(
                gateway,
                identity,
                workspaces,
                establisher,
                new LocalAccountProperties(true, SESSION_TTL),
                auditLog,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        controller.launch("code", new MockHttpServletRequest(), new MockHttpServletResponse());

        ArgumentCaptor<Instant> expiry = ArgumentCaptor.forClass(Instant.class);
        verify(establisher).establish(
                eq(user),
                eq(workspace),
                eq(7L),
                expiry.capture(),
                any(),
                any()
        );
        assertThat(expiry.getValue()).isEqualTo(NOW.plus(SESSION_TTL));
    }
}
