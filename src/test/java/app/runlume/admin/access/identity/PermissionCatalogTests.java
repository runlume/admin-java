package app.runlume.admin.access.identity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限码通配规则测试。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:30
 */
class PermissionCatalogTests {

    @Test
    void allCodeSatisfiesEveryRequirement() {
        assertThat(PermissionCatalog.grants(Set.of("*"), PermissionCatalog.NOTICE_MANAGE))
                .isTrue();
    }

    @Test
    void moduleWildcardOnlyCoversItsOwnModule() {
        assertThat(PermissionCatalog.grants(Set.of("user:*"), PermissionCatalog.USER_UPDATE))
                .isTrue();
        assertThat(PermissionCatalog.grants(Set.of("user:*"), PermissionCatalog.ROLE_VIEW))
                .isFalse();
    }

    @Test
    void expandsWildcardsIntoConcreteAuthorities() {
        Set<String> expanded = PermissionCatalog.expand(Set.of("*"));
        assertThat(expanded).doesNotContain("*");
        assertThat(expanded).contains(
                PermissionCatalog.USER_UPDATE,
                PermissionCatalog.NOTICE_MANAGE,
                PermissionCatalog.PLATFORM_VIEW
        );
    }

    @Test
    void keepsUnknownCodesForBusinessExtensions() {
        assertThat(PermissionCatalog.expand(Set.of("customer:view")))
                .containsExactly("customer:view");
    }

    @Test
    void validatesCodeFormat() {
        assertThat(PermissionCatalog.isValidCode("user:view")).isTrue();
        assertThat(PermissionCatalog.isValidCode("user:*")).isTrue();
        assertThat(PermissionCatalog.isValidCode("*")).isTrue();
        assertThat(PermissionCatalog.isValidCode("User:View")).isFalse();
        assertThat(PermissionCatalog.isValidCode("")).isFalse();
    }
}
