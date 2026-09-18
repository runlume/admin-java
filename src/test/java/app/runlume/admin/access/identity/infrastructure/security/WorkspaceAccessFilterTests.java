package app.runlume.admin.access.identity.infrastructure.security;

import app.runlume.admin.access.WorkspaceDirectory;
import app.runlume.admin.access.WorkspaceStatus;
import app.runlume.admin.access.WorkspaceView;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 工作区逐请求复验：状态收敛、边界不一致与本地会话豁免。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:02
 */
class WorkspaceAccessFilterTests {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID APP_INSTANCE_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(
            value = WorkspaceStatus.class,
            names = {"SUSPENDED", "DEPROVISIONING", "DEPROVISIONED"}
    )
    void rejectsSessionWhenWorkspaceNoLongerAcceptsAccess(WorkspaceStatus status) throws Exception {
        WorkspaceDirectory workspaces = mock(WorkspaceDirectory.class);
        when(workspaces.find(ACCOUNT_ID, APP_INSTANCE_ID))
                .thenReturn(Optional.of(workspace(status)));
        MockHttpSession session = authenticated(platformPrincipal());
        AtomicBoolean chained = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(workspaces).doFilter(
                requestWith(session),
                response,
                countInvocation(chained)
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chained).isFalse();
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsSessionWhenWorkspaceBoundaryMismatch() throws Exception {
        WorkspaceDirectory workspaces = mock(WorkspaceDirectory.class);
        when(workspaces.find(ACCOUNT_ID, APP_INSTANCE_ID))
                .thenReturn(Optional.of(workspace(
                        UUID.randomUUID(),
                        WorkspaceStatus.ACTIVE
                )));
        MockHttpSession session = authenticated(platformPrincipal());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(workspaces).doFilter(
                requestWith(session),
                response,
                countInvocation(new AtomicBoolean())
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void rejectsSessionWhenWorkspaceIsGone() throws Exception {
        WorkspaceDirectory workspaces = mock(WorkspaceDirectory.class);
        when(workspaces.find(ACCOUNT_ID, APP_INSTANCE_ID)).thenReturn(Optional.empty());
        MockHttpSession session = authenticated(platformPrincipal());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(workspaces).doFilter(
                requestWith(session),
                response,
                countInvocation(new AtomicBoolean())
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void continuesWhenWorkspaceIsActive() throws Exception {
        WorkspaceDirectory workspaces = mock(WorkspaceDirectory.class);
        when(workspaces.find(ACCOUNT_ID, APP_INSTANCE_ID))
                .thenReturn(Optional.of(workspace(WorkspaceStatus.ACTIVE)));
        MockHttpSession session = authenticated(platformPrincipal());
        AtomicBoolean chained = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(workspaces).doFilter(
                requestWith(session),
                response,
                countInvocation(chained)
        );

        assertThat(chained).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void skipsLocalSessionWithoutWorkspaceBoundary() throws Exception {
        WorkspaceDirectory workspaces = mock(WorkspaceDirectory.class);
        MockHttpSession session = authenticated(new AdminSessionPrincipal(
                UUID.randomUUID(),
                null,
                null,
                null,
                "local@runlume.local",
                "本地账号",
                Set.of("admin"),
                Set.of("*"),
                0L,
                Instant.now().plusSeconds(3600)
        ));
        AtomicBoolean chained = new AtomicBoolean();

        filter(workspaces).doFilter(
                requestWith(session),
                new MockHttpServletResponse(),
                countInvocation(chained)
        );

        assertThat(chained).isTrue();
        verifyNoInteractions(workspaces);
    }

    private static WorkspaceAccessFilter filter(WorkspaceDirectory workspaces) {
        return new WorkspaceAccessFilter(workspaces);
    }

    private static MockHttpServletRequest requestWith(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }

    private static MockHttpSession authenticated(AdminSessionPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
        );
        return new MockHttpSession();
    }

    private static FilterChain countInvocation(AtomicBoolean chained) {
        return (request, response) -> chained.set(true);
    }

    private static AdminSessionPrincipal platformPrincipal() {
        return new AdminSessionPrincipal(
                UUID.randomUUID(),
                WORKSPACE_ID,
                ACCOUNT_ID,
                APP_INSTANCE_ID,
                "member@runlume.local",
                "平台成员",
                Set.of("member"),
                Set.of("example.admin.notice.view"),
                5L,
                Instant.now().plusSeconds(300)
        );
    }

    private static WorkspaceView workspace(WorkspaceStatus status) {
        return workspace(WORKSPACE_ID, status);
    }

    private static WorkspaceView workspace(UUID id, WorkspaceStatus status) {
        return new WorkspaceView(
                id,
                ACCOUNT_ID,
                APP_INSTANCE_ID,
                "example.admin",
                "1.0.0",
                "ws_" + id,
                status,
                Instant.now(),
                Instant.now()
        );
    }
}
