package app.runlume.admin.access.identity.infrastructure.security;

import app.runlume.admin.access.SessionCheckProperties;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.admin.access.identity.IdentityProblem;
import app.runlume.admin.access.identity.SessionState;
import app.runlume.admin.access.identity.SessionValidationGateway;
import app.runlume.admin.access.identity.infrastructure.SessionValidationCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 成员校验过滤器：不活跃、平台失败关闭、本地会话豁免。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 17:30
 */
class SessionValidationFilterTests {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID APP_INSTANCE_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsSessionWhenMemberIsNoLongerActive() throws Exception {
        MockHttpSession session = authenticated(platformPrincipal());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        filter(gateway(false, false)).doFilter(
                request("GET", session),
                response,
                (request, result) -> chained.set(true)
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chained).isFalse();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void failsClosedWhenPlatformIsUnavailable() throws Exception {
        MockHttpSession session = authenticated(platformPrincipal());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        filter(gateway(true, true)).doFilter(
                request("POST", session),
                response,
                (request, result) -> chained.set(true)
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chained).isFalse();
    }

    @Test
    void continuesWhenMemberIsActive() throws Exception {
        MockHttpSession session = authenticated(platformPrincipal());
        AtomicBoolean chained = new AtomicBoolean();

        filter(gateway(true, false)).doFilter(
                request("GET", session),
                new MockHttpServletResponse(),
                (request, result) -> chained.set(true)
        );

        assertThat(chained).isTrue();
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void skipsLocalSessionWithoutWorkspaceBoundary() throws Exception {
        SecurityContextHolder.clearContext();
        authenticated(localPrincipal());
        AtomicBoolean localChained = new AtomicBoolean();
        filter(gateway(false, true)).doFilter(
                request("GET", new MockHttpSession()),
                new MockHttpServletResponse(),
                (request, result) -> localChained.set(true)
        );

        assertThat(localChained).isTrue();
    }

    private static MockHttpServletRequest request(String method, MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/notices");
        request.setSession(session);
        return request;
    }

    private static SessionValidationFilter filter(SessionValidationGateway gateway) {
        return new SessionValidationFilter(new SessionValidationCache(
                gateway,
                new SessionCheckProperties(Duration.ofSeconds(60), Duration.ofSeconds(30)),
                Clock.fixed(Instant.parse("2026-09-18T08:30:00Z"), ZoneOffset.UTC)
        ));
    }

    private static SessionValidationGateway gateway(boolean active, boolean failing) {
        return (accountId, appInstanceId, userId) -> {
            if (failing) {
                throw IdentityProblem.of(IdentityProblem.Code.PLATFORM_UNAVAILABLE);
            }
            return new SessionState(userId, active, Set.of("member"), 1L);
        };
    }

    private static MockHttpSession authenticated(AdminSessionPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
        );
        MockHttpSession session = new MockHttpSession();
        return session;
    }

    private static AdminSessionPrincipal platformPrincipal() {
        return new AdminSessionPrincipal(
                USER_ID,
                WORKSPACE_ID,
                ACCOUNT_ID,
                APP_INSTANCE_ID,
                "member@runlume.local",
                "平台成员",
                Set.of("member"),
                Set.of("example.admin.notice.view"),
                1L,
                Instant.parse("2026-09-18T20:00:00Z")
        );
    }

    private static AdminSessionPrincipal localPrincipal() {
        return new AdminSessionPrincipal(
                UUID.randomUUID(),
                null,
                null,
                null,
                "ops@runlume.local",
                "本地运营",
                Set.of("admin"),
                Set.of("*"),
                0L,
                Instant.parse("2026-09-18T20:00:00Z")
        );
    }
}
