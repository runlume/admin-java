package app.runlume.admin.access.identity.infrastructure.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 按账号主体撤销全部服务端会话。
 *
 * <p>账号被禁用或口令被重置后必须立即撤销既有会话，而不是等会话自然过期。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:20
 */
@Component
public class AdminSessionRevocation {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    /**
     * 创建会话撤销器。
     *
     * @param sessions 支持按主体名称检索的会话仓库
     */
    public AdminSessionRevocation(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /**
     * 撤销某个账号的全部会话。
     *
     * @param principalName 会话主体名称，即登录邮箱
     * @return 被撤销的会话数量
     */
    public int revokeAll(String principalName) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(principalName);
        found.values().forEach(session -> sessions.deleteById(session.getId()));
        return found.size();
    }
}
