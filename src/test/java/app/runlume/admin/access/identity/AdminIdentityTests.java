package app.runlume.admin.access.identity;

import app.runlume.admin.support.PostgresTestSupport;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static app.runlume.admin.access.identity.infrastructure.jooq.tables.AdminUser.ADMIN_USER;
import static app.runlume.admin.access.identity.infrastructure.jooq.tables.AdminUserRole.ADMIN_USER_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地身份、角色授予与失败分类。
 *
 * <p>用例在事务内执行并回滚，保证 {@code 首位注册者成为管理员} 的判定不被其它用例影响。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:40
 */
@Transactional
class AdminIdentityTests extends PostgresTestSupport {

    @Autowired
    private AdminIdentity identity;

    @Autowired
    private DSLContext dsl;

    @Test
    void firstRegisteredAccountBecomesAdministrator() {
        clearAccounts();
        AdminUserView first = identity.register("first@runlume.local", "首位", "runlume-password");
        assertThat(first.roles()).containsExactly("admin");
        assertThat(first.permissions()).containsExactly("*");

        AdminUserView second = identity.register("second@runlume.local", "次位", "runlume-password");
        assertThat(second.roles()).containsExactly("member");
        assertThat(second.permissions()).contains("notice:view");
    }

    @Test
    void unknownRoleIsRejected() {
        clearAccounts();
        assertThatThrownBy(() -> identity.createUser(
                "role-" + UUID.randomUUID() + "@runlume.local",
                "角色校验",
                "runlume-password",
                Set.of("nonexistent")
        ))
                .isInstanceOf(IdentityProblem.class)
                .extracting(problem -> ((IdentityProblem) problem).code())
                .isEqualTo(IdentityProblem.Code.ROLE_NOT_FOUND);
    }

    @Test
    void passwordIsNeverReturnedInViews() {
        clearAccounts();
        AdminUserView user = identity.register("hash@runlume.local", "摘要", "runlume-password");
        String hash = dsl.select(ADMIN_USER.PASSWORD_HASH)
                .from(ADMIN_USER)
                .where(ADMIN_USER.ID.eq(user.id()))
                .fetchOne(ADMIN_USER.PASSWORD_HASH);
        assertThat(hash).startsWith("$2");
        assertThat(user.toString()).doesNotContain(hash);
    }

    private void clearAccounts() {
        dsl.deleteFrom(ADMIN_USER_ROLE).execute();
        dsl.deleteFrom(ADMIN_USER).execute();
    }
}
