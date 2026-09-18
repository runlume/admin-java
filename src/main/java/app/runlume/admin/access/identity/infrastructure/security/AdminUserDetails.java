package app.runlume.admin.access.identity.infrastructure.security;

import app.runlume.admin.access.identity.AdminUserView;
import app.runlume.admin.access.identity.PermissionCatalog;
import app.runlume.admin.access.identity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 口令认证阶段使用的账号细节，只存在于一次登录请求内。
 *
 * <p>授权集合是 {@link PermissionCatalog#expand} 展开后的具体权限码，因此
 * {@code *} 与 {@code 模块:*} 同样能通过 {@code hasAuthority} 判定。</p>
 *
 * @param user 账号视图
 * @param passwordHash BCrypt 口令摘要
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:20
 */
public record AdminUserDetails(AdminUserView user, String passwordHash) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = PermissionCatalog
                .expand(user.permissions())
                .stream()
                .sorted()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return user.email();
    }

    @Override
    public boolean isEnabled() {
        return user.status() == UserStatus.ACTIVE;
    }
}
