package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.SessionCheckProperties;
import app.runlume.admin.access.identity.AdminSessionPrincipal;
import app.runlume.admin.access.identity.SessionState;
import app.runlume.admin.access.identity.SessionValidationGateway;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按窗口缓存平台会话校验结果。
 *
 * <p>撤销窗口等于声明的缓存窗口：写请求使用更短窗口，读请求使用较长窗口。缓存只保存
 * 可重建的校验结论，失败不写入缓存，使平台故障不会被放大成授权结论。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 14:35
 */
@Component
public class SessionValidationCache {

    private static final int MAX_ENTRIES = 10_000;

    private final SessionValidationGateway gateway;
    private final SessionCheckProperties properties;
    private final Clock clock;
    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

    /**
     * 创建会话校验缓存。
     *
     * @param gateway    平台校验端口
     * @param properties 窗口配置
     * @param clock      判定时间源
     */
    public SessionValidationCache(
            SessionValidationGateway gateway,
            SessionCheckProperties properties,
            Clock clock
    ) {
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 返回仍有效的成员会话状态，必要时向平台重新校验。
     *
     * @param principal 当前已认证会话主体；必须绑定工作区
     * @param writeRequest 当前请求是否为写请求
     * @return 平台给出的会话状态
     * @throws app.runlume.admin.access.identity.IdentityProblem 平台不可用、拒绝或响应不符合契约
     */
    public SessionState validate(AdminSessionPrincipal principal, boolean writeRequest) {
        Duration window = writeRequest ? properties.writeWindow() : properties.readWindow();
        Key key = new Key(principal.platformAppInstanceId(), principal.userId());
        Instant now = Instant.now(clock);
        Entry cached = entries.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.state();
        }
        SessionState state = gateway.validate(
                principal.platformAccountId(),
                principal.platformAppInstanceId(),
                principal.userId()
        );
        if (entries.size() >= MAX_ENTRIES) {
            entries.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
        }
        entries.put(key, new Entry(state, now.plus(window)));
        return state;
    }

    /**
     * 缓存键：同一平台用户在同一个实例内的校验结论。
     *
     * @param appInstanceId 平台 AppInstance 标识
     * @param userId        平台用户标识
     */
    private record Key(UUID appInstanceId, UUID userId) {
    }

    /**
     * 带截止时间的缓存条目。
     *
     * @param state     校验结论
     * @param expiresAt 本地失效时间
     */
    private record Entry(SessionState state, Instant expiresAt) {
    }
}
