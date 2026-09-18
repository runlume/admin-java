package app.runlume.admin.access.identity.infrastructure.security;

import app.runlume.admin.access.identity.AdminCredentials;
import app.runlume.admin.access.identity.AdminIdentity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 把本地账号接入 Spring Security 的口令认证。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:20
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminIdentity identity;

    /**
     * 创建账号细节服务。
     *
     * @param identity 本地身份入口
     */
    public AdminUserDetailsService(AdminIdentity identity) {
        this.identity = identity;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return identity.findCredentials(username)
                .map(AdminUserDetailsService::toDetails)
                .orElseThrow(() -> new UsernameNotFoundException("account not found"));
    }

    private static AdminUserDetails toDetails(AdminCredentials credentials) {
        return new AdminUserDetails(credentials.user(), credentials.passwordHash());
    }
}
