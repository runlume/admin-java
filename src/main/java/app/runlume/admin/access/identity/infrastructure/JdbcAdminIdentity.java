package app.runlume.admin.access.identity.infrastructure;

import app.runlume.admin.access.identity.AdminCredentials;
import app.runlume.admin.access.identity.AdminIdentity;
import app.runlume.admin.access.identity.AdminRoleView;
import app.runlume.admin.access.identity.AdminUserView;
import app.runlume.admin.access.identity.IdentityProblem;
import app.runlume.admin.access.identity.PlatformLaunchIdentity;
import app.runlume.admin.access.identity.UserStatus;
import app.runlume.admin.access.identity.infrastructure.jooq.tables.records.AdminUserRecord;
import app.runlume.platform.sdk.identity.InstanceRoles;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static app.runlume.admin.access.identity.infrastructure.jooq.tables.AdminRole.ADMIN_ROLE;
import static app.runlume.admin.access.identity.infrastructure.jooq.tables.AdminRolePermission.ADMIN_ROLE_PERMISSION;
import static app.runlume.admin.access.identity.infrastructure.jooq.tables.AdminUser.ADMIN_USER;
import static app.runlume.admin.access.identity.infrastructure.jooq.tables.AdminUserRole.ADMIN_USER_ROLE;

/**
 * 使用 jOOQ 实现本地账号、角色与权限的读写。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:10
 */
@Repository
public class JdbcAdminIdentity implements AdminIdentity {

    private static final String CREDENTIAL_LOCAL = "LOCAL";
    private static final String CREDENTIAL_PLATFORM = "PLATFORM";
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_MEMBER = "member";
    private static final String ROLE_SOURCE_LOCAL = "LOCAL";
    private static final String ROLE_SOURCE_PLATFORM = "PLATFORM";
    private static final String REGISTRATION_LOCK = "admin-java.registration";
    private static final String PLATFORM_EMAIL_DOMAIN = "runlume.local";
    private static final String UNIQUE_VIOLATION = "23505";

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建身份读写实现。
     *
     * @param dsl jOOQ 上下文
     * @param passwordEncoder 口令编码器
     */
    public JdbcAdminIdentity(DSLContext dsl, PasswordEncoder passwordEncoder) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public AdminUserView register(String email, String displayName, String rawPassword) {
        dsl.fetch("select pg_advisory_xact_lock(hashtext(?)::bigint)", REGISTRATION_LOCK);
        boolean firstAccount = dsl.fetchCount(ADMIN_USER) == 0;
        UUID userId = insertLocalUser(email, displayName, rawPassword);
        replaceLocalRoles(userId, Set.of(firstAccount ? ROLE_ADMIN : ROLE_MEMBER));
        return loadUser(userId);
    }

    @Override
    @Transactional
    public AdminUserView createUser(
            String email,
            String displayName,
            String rawPassword,
            Set<String> roleCodes
    ) {
        UUID userId = insertLocalUser(email, displayName, rawPassword);
        replaceLocalRoles(userId, roleCodes.isEmpty() ? Set.of(ROLE_MEMBER) : roleCodes);
        return loadUser(userId);
    }

    @Override
    public Optional<AdminCredentials> findCredentials(String email) {
        return dsl.selectFrom(ADMIN_USER)
                .where(ADMIN_USER.EMAIL.eq(normalizeEmail(email)))
                .and(ADMIN_USER.CREDENTIAL_SOURCE.eq(CREDENTIAL_LOCAL))
                .fetchOptional()
                .map(record -> new AdminCredentials(toView(record), requireHash(record)));
    }

    @Override
    public Optional<AdminUserView> findUser(UUID workspaceId, UUID userId) {
        return dsl.selectFrom(ADMIN_USER)
                .where(ADMIN_USER.ID.eq(userId))
                .and(ADMIN_USER.WORKSPACE_ID.eq(workspaceId))
                .fetchOptional()
                .map(this::toView);
    }

    @Override
    public List<AdminUserView> listUsers(
            UUID workspaceId,
            int offset,
            int limit,
            String keyword
    ) {
        List<AdminUserRecord> records = dsl.selectFrom(ADMIN_USER)
                .where(ADMIN_USER.WORKSPACE_ID.eq(workspaceId))
                .and(keywordCondition(keyword))
                .orderBy(ADMIN_USER.CREATED_AT.desc(), ADMIN_USER.ID.asc())
                .limit(Math.max(limit, 1))
                .offset(Math.max(offset, 0))
                .fetch();
        return assemble(records);
    }

    @Override
    public long countUsers(UUID workspaceId, String keyword) {
        return dsl.fetchCount(dsl.selectFrom(ADMIN_USER)
                .where(ADMIN_USER.WORKSPACE_ID.eq(workspaceId))
                .and(keywordCondition(keyword)));
    }

    @Override
    @Transactional
    public AdminUserView renameUser(UUID workspaceId, UUID userId, String displayName) {
        int updated = dsl.update(ADMIN_USER)
                .set(ADMIN_USER.DISPLAY_NAME, displayName)
                .set(ADMIN_USER.UPDATED_AT, OffsetDateTime.now())
                .set(ADMIN_USER.VERSION, ADMIN_USER.VERSION.plus(1))
                .where(ADMIN_USER.ID.eq(userId))
                .and(ADMIN_USER.WORKSPACE_ID.eq(workspaceId))
                .execute();
        if (updated == 0) {
            throw IdentityProblem.of(IdentityProblem.Code.USER_NOT_FOUND);
        }
        return requireUser(workspaceId, userId);
    }

    @Override
    @Transactional
    public AdminUserView changeStatus(UUID workspaceId, UUID userId, UserStatus status) {
        int updated = dsl.update(ADMIN_USER)
                .set(ADMIN_USER.STATUS, status.name())
                .set(ADMIN_USER.UPDATED_AT, OffsetDateTime.now())
                .set(ADMIN_USER.VERSION, ADMIN_USER.VERSION.plus(1))
                .where(ADMIN_USER.ID.eq(userId))
                .and(ADMIN_USER.WORKSPACE_ID.eq(workspaceId))
                .execute();
        if (updated == 0) {
            throw IdentityProblem.of(IdentityProblem.Code.USER_NOT_FOUND);
        }
        return requireUser(workspaceId, userId);
    }

    @Override
    @Transactional
    public AdminUserView changeRoles(UUID workspaceId, UUID userId, Set<String> roleCodes) {
        if (findUser(workspaceId, userId).isEmpty()) {
            throw IdentityProblem.of(IdentityProblem.Code.USER_NOT_FOUND);
        }
        replaceLocalRoles(userId, roleCodes.isEmpty() ? Set.of(ROLE_MEMBER) : roleCodes);
        return requireUser(workspaceId, userId);
    }

    @Override
    @Transactional
    public AdminUserView upsertPlatformUser(PlatformLaunchIdentity launch, UUID workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        String email = platformEmail(launch);
        Optional<AdminUserRecord> existing = dsl.selectFrom(ADMIN_USER)
                .where(ADMIN_USER.PLATFORM_APP_INSTANCE_ID.eq(launch.platformAppInstanceId()))
                .and(ADMIN_USER.PLATFORM_USER_ID.eq(launch.platformUserId()))
                .fetchOptional();
        if (existing.isPresent()) {
            dsl.update(ADMIN_USER)
                    .set(ADMIN_USER.EMAIL, email)
                    .set(ADMIN_USER.DISPLAY_NAME, launch.displayName())
                    .set(
                            ADMIN_USER.MEMBERSHIP_REVISION,
                            Math.max(
                                    existing.get().getMembershipRevision(),
                                    launch.membershipRevision()
                            )
                    )
                    .set(ADMIN_USER.WORKSPACE_ID, workspaceId)
                    .set(ADMIN_USER.UPDATED_AT, OffsetDateTime.now())
                    .set(ADMIN_USER.VERSION, ADMIN_USER.VERSION.plus(1))
                    .where(ADMIN_USER.ID.eq(existing.get().getId()))
                    .execute();
            syncPlatformRoles(existing.get().getId(), platformRoleCodes(launch));
            return loadUser(existing.get().getId());
        }
        AdminUserRecord record = dsl.newRecord(ADMIN_USER);
        record.setId(UUID.randomUUID());
        record.setEmail(email);
        record.setDisplayName(launch.displayName());
        record.setCredentialSource(CREDENTIAL_PLATFORM);
        record.setPlatformUserId(launch.platformUserId());
        record.setPlatformAccountId(launch.platformAccountId());
        record.setPlatformAppInstanceId(launch.platformAppInstanceId());
        record.setWorkspaceId(workspaceId);
        record.setMembershipRevision(launch.membershipRevision());
        record.setStatus(UserStatus.ACTIVE.name());
        record.setVersion(0L);
        record.store();
        // 平台成员首次建档时显式分配内置 member，而不是依赖隐式放行。
        replaceLocalRoles(record.getId(), Set.of(ROLE_MEMBER));
        syncPlatformRoles(record.getId(), platformRoleCodes(launch));
        return loadUser(record.getId());
    }

    @Override
    public void recordLogin(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.update(ADMIN_USER)
                .set(ADMIN_USER.LAST_LOGIN_AT, now)
                .set(ADMIN_USER.UPDATED_AT, now)
                .where(ADMIN_USER.ID.eq(userId))
                .execute();
    }

    @Override
    public List<AdminRoleView> listRoles() {
        List<AdminRoleRecordView> roles = dsl.selectFrom(ADMIN_ROLE)
                .orderBy(ADMIN_ROLE.BUILT_IN.desc(), ADMIN_ROLE.CODE.asc())
                .fetch(record -> new AdminRoleRecordView(
                        record.getId(),
                        record.getCode(),
                        record.getName(),
                        record.getDescription(),
                        record.getBuiltIn()
                ));
        Map<String, Set<String>> permissions = loadPermissionsByRole(
                roles.stream().map(AdminRoleRecordView::code).toList()
        );
        return roles.stream()
                .map(role -> new AdminRoleView(
                        role.id(),
                        role.code(),
                        role.name(),
                        role.description(),
                        role.builtIn(),
                        permissions.getOrDefault(role.code(), Set.of())
                ))
                .toList();
    }

    private UUID insertLocalUser(String email, String displayName, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (localEmailExists(normalizedEmail)) {
            throw IdentityProblem.of(IdentityProblem.Code.EMAIL_ALREADY_REGISTERED);
        }
        AdminUserRecord record = dsl.newRecord(ADMIN_USER);
        record.setId(UUID.randomUUID());
        record.setEmail(normalizedEmail);
        record.setDisplayName(displayName);
        record.setPasswordHash(passwordEncoder.encode(rawPassword));
        record.setCredentialSource(CREDENTIAL_LOCAL);
        record.setStatus(UserStatus.ACTIVE.name());
        record.setVersion(0L);
        try {
            record.store();
        } catch (DataAccessException exception) {
            if (isUniqueViolation(exception)) {
                throw IdentityProblem.of(IdentityProblem.Code.EMAIL_ALREADY_REGISTERED);
            }
            throw exception;
        }
        return record.getId();
    }

    /**
     * 整体替换本地角色授予，保留平台派生授予。
     */
    private void replaceLocalRoles(UUID userId, Set<String> roleCodes) {
        Map<String, UUID> roleIds = requireRoleIds(roleCodes);
        dsl.deleteFrom(ADMIN_USER_ROLE)
                .where(ADMIN_USER_ROLE.USER_ID.eq(userId))
                .and(ADMIN_USER_ROLE.SOURCE.eq(ROLE_SOURCE_LOCAL))
                .execute();
        insertRoleLinks(userId, roleIds.values(), ROLE_SOURCE_LOCAL);
    }

    /**
     * 用当前实例角色同步平台派生授予；平台撤销 SAAS_ADMIN 后不再保留管理员。
     */
    private void syncPlatformRoles(UUID userId, Set<String> roleCodes) {
        Map<String, UUID> roleIds = requireRoleIds(roleCodes);
        dsl.deleteFrom(ADMIN_USER_ROLE)
                .where(ADMIN_USER_ROLE.USER_ID.eq(userId))
                .and(ADMIN_USER_ROLE.SOURCE.eq(ROLE_SOURCE_PLATFORM))
                .execute();
        insertRoleLinks(userId, roleIds.values(), ROLE_SOURCE_PLATFORM);
    }

    private Map<String, UUID> requireRoleIds(Set<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, UUID> roleIds = dsl.select(ADMIN_ROLE.CODE, ADMIN_ROLE.ID)
                .from(ADMIN_ROLE)
                .where(ADMIN_ROLE.CODE.in(roleCodes))
                .fetchMap(ADMIN_ROLE.CODE, ADMIN_ROLE.ID);
        if (roleIds.size() != roleCodes.size()) {
            throw IdentityProblem.of(IdentityProblem.Code.ROLE_NOT_FOUND);
        }
        return roleIds;
    }

    private void insertRoleLinks(UUID userId, Collection<UUID> roleIds, String source) {
        roleIds.forEach(roleId -> {
            var link = dsl.newRecord(ADMIN_USER_ROLE);
            link.setUserId(userId);
            link.setRoleId(roleId);
            link.setSource(source);
            link.store();
        });
    }

    /**
     * 平台实例角色到本地内置角色的派生：SAAS_ADMIN 固定派生内置管理员。
     */
    private static Set<String> platformRoleCodes(PlatformLaunchIdentity launch) {
        return InstanceRoles.hasAdministrator(launch.roles())
                ? Set.of(ROLE_ADMIN)
                : Set.of();
    }

    private AdminUserView requireUser(UUID workspaceId, UUID userId) {
        return findUser(workspaceId, userId).orElseThrow(
                () -> IdentityProblem.of(IdentityProblem.Code.USER_NOT_FOUND)
        );
    }

    private AdminUserView loadUser(UUID userId) {
        return dsl.selectFrom(ADMIN_USER)
                .where(ADMIN_USER.ID.eq(userId))
                .fetchOptional()
                .map(this::toView)
                .orElseThrow(() -> IdentityProblem.of(IdentityProblem.Code.USER_NOT_FOUND));
    }

    private List<AdminUserView> assemble(List<AdminUserRecord> records) {
        List<UUID> userIds = records.stream().map(AdminUserRecord::getId).toList();
        Map<UUID, Set<String>> rolesByUser = loadRoleCodesByUser(userIds);
        Set<String> allRoles = rolesByUser.values().stream()
                .flatMap(Collection::stream)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String, Set<String>> permissionsByRole = loadPermissionsByRole(allRoles);
        return records.stream()
                .map(record -> {
                    Set<String> roles = rolesByUser.getOrDefault(record.getId(), Set.of());
                    return toView(record, roles, permissionsOf(roles, permissionsByRole));
                })
                .toList();
    }

    private AdminUserView toView(AdminUserRecord record) {
        Set<String> roles = loadRoleCodesByUser(List.of(record.getId()))
                .getOrDefault(record.getId(), Set.of());
        return toView(record, roles, loadPermissions(roles));
    }

    private static AdminUserView toView(
            AdminUserRecord record,
            Set<String> roles,
            Set<String> permissions
    ) {
        return new AdminUserView(
                record.getId(),
                record.getEmail(),
                record.getDisplayName(),
                UserStatus.valueOf(record.getStatus()),
                roles,
                permissions,
                toInstant(record.getCreatedAt()),
                toInstant(record.getUpdatedAt()),
                toInstant(record.getLastLoginAt())
        );
    }

    private Map<UUID, Set<String>> loadRoleCodesByUser(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<String>> result = new HashMap<>();
        dsl.select(ADMIN_USER_ROLE.USER_ID, ADMIN_ROLE.CODE)
                .from(ADMIN_USER_ROLE)
                .join(ADMIN_ROLE).on(ADMIN_ROLE.ID.eq(ADMIN_USER_ROLE.ROLE_ID))
                .where(ADMIN_USER_ROLE.USER_ID.in(userIds))
                .fetch()
                .forEach(record -> result
                        .computeIfAbsent(record.value1(), key -> new LinkedHashSet<>())
                        .add(record.value2()));
        return result;
    }

    private Map<String, Set<String>> loadPermissionsByRole(Collection<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> result = new HashMap<>();
        dsl.select(ADMIN_ROLE.CODE, ADMIN_ROLE_PERMISSION.PERMISSION_CODE)
                .from(ADMIN_ROLE_PERMISSION)
                .join(ADMIN_ROLE).on(ADMIN_ROLE.ID.eq(ADMIN_ROLE_PERMISSION.ROLE_ID))
                .where(ADMIN_ROLE.CODE.in(roleCodes))
                .fetch()
                .forEach(record -> result
                        .computeIfAbsent(record.value1(), key -> new LinkedHashSet<>())
                        .add(record.value2()));
        return result;
    }

    private Set<String> loadPermissions(Set<String> roleCodes) {
        return Set.copyOf(permissionsOf(roleCodes, loadPermissionsByRole(roleCodes)));
    }

    private static Set<String> permissionsOf(
            Set<String> roleCodes,
            Map<String, Set<String>> permissionsByRole
    ) {
        Set<String> permissions = new LinkedHashSet<>();
        roleCodes.forEach(role -> permissions.addAll(
                permissionsByRole.getOrDefault(role, Set.of())
        ));
        return Set.copyOf(permissions);
    }

    private boolean localEmailExists(String email) {
        return dsl.fetchExists(dsl.selectOne()
                .from(ADMIN_USER)
                .where(ADMIN_USER.EMAIL.eq(email))
                .and(ADMIN_USER.CREDENTIAL_SOURCE.eq(CREDENTIAL_LOCAL)));
    }

    private static org.jooq.Condition keywordCondition(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return org.jooq.impl.DSL.noCondition();
        }
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return ADMIN_USER.EMAIL.like(pattern).or(ADMIN_USER.DISPLAY_NAME.like(pattern));
    }

    private static String platformEmail(PlatformLaunchIdentity launch) {
        String email = launch.email();
        if (email != null && !email.isBlank()) {
            return normalizeEmail(email);
        }
        return "platform+" + launch.platformUserId() + "@" + PLATFORM_EMAIL_DOMAIN;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireHash(AdminUserRecord record) {
        String hash = record.getPasswordHash();
        if (hash == null || hash.isBlank()) {
            throw IdentityProblem.of(IdentityProblem.Code.INVALID_CREDENTIALS);
        }
        return hash;
    }

    private static boolean isUniqueViolation(DataAccessException exception) {
        return exception.getCause() instanceof SQLException sql
                && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }

    private static java.time.Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private record AdminRoleRecordView(
            UUID id,
            String code,
            String name,
            String description,
            boolean builtIn
    ) {
    }
}
