package app.runlume.admin;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Modulith 架构门禁。
 *
 * <p>断言 {@code access} 是平台集成 owning module，业务域只能通过
 * {@code access.identity} 与 {@code access.observability} 两个 Named Interface 访问它。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:30
 */
class AdminModulesTests {

    private static final ApplicationModules MODULES =
            ApplicationModules.of(AdminJavaApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        MODULES.verify();
    }

    @Test
    void exposesPlatformIntegrationModule() {
        assertThat(MODULES.getModuleByName("access")).isNotNull();
    }
}
