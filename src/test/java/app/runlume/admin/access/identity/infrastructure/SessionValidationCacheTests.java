package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.SessionCheckProperties;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.admin.access.identity.IdentityProblem;
import app.runlume.admin.access.identity.SessionState;
import app.runlume.admin.access.identity.SessionValidationGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话校验窗口：读写窗口分离、命中不重复调用、失败不写缓存。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:40
 */
class SessionValidationCacheTests {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID APP_INSTANCE_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-09-18T06:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private static final class CountingGateway implements SessionValidationGateway {

        private final AtomicInteger calls = new AtomicInteger();
        private boolean active = true;
        private boolean failing;

        @Override
        public SessionState validate(
                UUID platformAccountId,
                UUID platformAppInstanceId,
                UUID platformUserId
        ) {
            calls.incrementAndGet();
            if (failing) {
                throw IdentityProblem.of(IdentityProblem.Code.PLATFORM_UNAVAILABLE);
            }
            return new SessionState(platformUserId, active, Set.of("SAAS_ADMIN"), 5L);
        }
    }

    @Test
    void reusesResultInsideWindowAndRefreshesAfterExpiry() {
        MutableClock clock = new MutableClock();
        CountingGateway gateway = new CountingGateway();
        SessionValidationCache cache = cache(gateway, clock);

        assertThat(cache.validate(principal(), false).active()).isTrue();
        assertThat(cache.validate(principal(), false).active()).isTrue();
        assertThat(gateway.calls).hasValue(1);

        gateway.active = false;
        clock.advance(Duration.ofSeconds(61));
        assertThat(cache.validate(principal(), false).active()).isFalse();
        assertThat(gateway.calls).hasValue(2);
    }

    @Test
    void writeRequestsUseShorterWindow() {
        MutableClock clock = new MutableClock();
        CountingGateway gateway = new CountingGateway();
        SessionValidationCache cache = cache(gateway, clock);

        cache.validate(principal(), true);
        clock.advance(Duration.ofSeconds(31));
        cache.validate(principal(), true);

        assertThat(gateway.calls).hasValue(2);
    }

    @Test
    void failedValidationIsNotCached() {
        MutableClock clock = new MutableClock();
        CountingGateway gateway = new CountingGateway();
        gateway.failing = true;
        SessionValidationCache cache = cache(gateway, clock);

        assertThatThrownBy(() -> cache.validate(principal(), false))
                .isInstanceOf(IdentityProblem.class);
        gateway.failing = false;
        assertThat(cache.validate(principal(), false).active()).isTrue();

        assertThat(gateway.calls).hasValue(2);
    }

    private static SessionValidationCache cache(
            SessionValidationGateway gateway,
            Clock clock
    ) {
        return new SessionValidationCache(
                gateway,
                new SessionCheckProperties(Duration.ofSeconds(60), Duration.ofSeconds(30)),
                clock
        );
    }

    private static AdminSessionPrincipal principal() {
        return new AdminSessionPrincipal(
                USER_ID,
                WORKSPACE_ID,
                ACCOUNT_ID,
                APP_INSTANCE_ID,
                "member@runlume.local",
                "平台成员",
                Set.of("member"),
                Set.of("example.admin.notice.view"),
                5L,
                Instant.parse("2026-09-18T14:00:00Z")
        );
    }
}
