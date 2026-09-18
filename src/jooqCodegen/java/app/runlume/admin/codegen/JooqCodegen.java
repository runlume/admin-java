package app.runlume.admin.codegen;

import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

/**
 * 从完整 Flyway Migration 生成按 owning module 隔离的 jOOQ 类型。
 *
 * <p>该启动器只存在于构建类路径。每次实际生成都创建临时 PostgreSQL 18，禁止从开发者
 * 已有数据库或未迁移的 SQL 片段生成。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:20
 */
public final class JooqCodegen {

    private static final String POSTGRES_IMAGE = "postgres:18.4";
    private static final String DATABASE_NAME = "admin_codegen";
    private static final String DATABASE_USER = "admin_codegen";
    private static final String DATABASE_PASSWORD = "codegen-only-password";

    private JooqCodegen() {
    }

    /**
     * 迁移临时数据库并生成各 owning module 的表类型。
     *
     * @param arguments Migration 目录和生成源码根目录
     * @throws Exception 临时数据库、迁移或生成失败
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException(
                    "Expected migration directory and generated source directory."
            );
        }
        Path migrationDirectory = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path outputDirectory = Path.of(arguments[1]).toAbsolutePath().normalize();
        if (!Files.isDirectory(migrationDirectory)) {
            throw new IllegalArgumentException("Migration directory does not exist.");
        }

        replaceDirectory(outputDirectory);
        try (var postgres = new PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName(DATABASE_NAME)
                .withUsername(DATABASE_USER)
                .withPassword(DATABASE_PASSWORD)) {
            postgres.start();
            migrate(postgres, migrationDirectory);
            generateModule(
                    postgres,
                    outputDirectory.resolve("access"),
                    "app.runlume.admin.access.infrastructure.jooq",
                    "admin_workspace|admin_lifecycle_operation",
                    Set.of("AdminLifecycleOperation.java", "AdminWorkspace.java")
            );
            generateModule(
                    postgres,
                    outputDirectory.resolve("identity"),
                    "app.runlume.admin.access.identity.infrastructure.jooq",
                    "admin_user|admin_role|admin_role_permission|admin_user_role",
                    Set.of(
                            "AdminRole.java",
                            "AdminRolePermission.java",
                            "AdminUser.java",
                            "AdminUserRole.java"
                    )
            );
            generateModule(
                    postgres,
                    outputDirectory.resolve("observability"),
                    "app.runlume.admin.access.observability.infrastructure.jooq",
                    "admin_audit_event",
                    Set.of("AdminAuditEvent.java")
            );
            generateModule(
                    postgres,
                    outputDirectory.resolve("notice"),
                    "app.runlume.admin.notice.infrastructure.jooq",
                    "admin_notice",
                    Set.of("AdminNotice.java")
            );
        }
    }

    private static void migrate(PostgreSQLContainer postgres, Path migrationDirectory) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:" + migrationDirectory)
                .load()
                .migrate();
    }

    private static void generateModule(
            PostgreSQLContainer postgres,
            Path outputDirectory,
            String packageName,
            String tableIncludes,
            Set<String> expectedTableFiles
    ) throws Exception {
        GenerationTool.generate(new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(postgres.getJdbcUrl())
                        .withUser(postgres.getUsername())
                        .withPassword(postgres.getPassword()))
                .withGenerator(new Generator()
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withInputSchema("public")
                                .withIncludes(tableIncludes))
                        .withGenerate(new Generate()
                                .withDeprecated(false)
                                .withIndexes(true)
                                .withRelations(true)
                                .withRecords(true)
                                .withPojos(false)
                                .withDaos(false)
                                .withJavaTimeTypes(true))
                        .withTarget(new Target()
                                .withPackageName(packageName)
                                .withDirectory(outputDirectory.toString()))));
        verifyGeneratedTables(outputDirectory, packageName, expectedTableFiles);
    }

    private static void verifyGeneratedTables(
            Path outputDirectory,
            String packageName,
            Set<String> expectedTableFiles
    ) throws IOException {
        Path tableDirectory = outputDirectory
                .resolve(packageName.replace('.', '/'))
                .resolve("tables");
        Set<String> actualTableFiles;
        try (var files = Files.list(tableDirectory)) {
            actualTableFiles = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if (!actualTableFiles.equals(expectedTableFiles)) {
            throw new IllegalStateException(
                    "Unexpected generated tables for " + packageName + ": " + actualTableFiles
            );
        }
    }

    private static void replaceDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(directory);
    }
}
