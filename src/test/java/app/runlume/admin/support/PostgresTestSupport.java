package app.runlume.admin.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 集成测试基类：为整个测试 JVM 提供唯一 PostgreSQL 18。
 *
 * <p>容器在类初始化时启动并保持到 JVM 退出，由 Testcontainers 回收；这样所有测试类共享
 * 同一个 Spring 上下文缓存，不会出现“上下文缓存复用旧连接池、容器却已被重启”的假失败。</p>
 *
 * <p>数据库结构只由 Flyway Migration 建立，测试不允许手工建表，避免迁移与代码漂移。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:30
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PostgresTestSupport {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    static {
        POSTGRES.start();
    }

    /**
     * 把共享容器注入数据源配置。
     *
     * @param registry 动态属性注册表
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
