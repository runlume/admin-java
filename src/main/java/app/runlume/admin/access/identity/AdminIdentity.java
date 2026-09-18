package app.runlume.admin.access.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 本地账号、角色与权限的统一入口。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:50
 */
public interface AdminIdentity {

    /**
     * 公开注册一个自有口令账号。
     *
     * <p>系统内还没有任何账号时授予内置 {@code admin} 角色，否则授予 {@code member}，
     * 保证新部署能够立即进入后台。</p>
     *
     * @param email 登录邮箱，大小写不敏感
     * @param displayName 展示名称
     * @param rawPassword 明文口令，只在本方法内使用
     * @return 新账号视图
     * @throws IdentityProblem 邮箱已被占用
     */
    AdminUserView register(String email, String displayName, String rawPassword);

    /**
     * 由后台管理员创建账号并显式指定角色。
     *
     * @param email 登录邮箱
     * @param displayName 展示名称
     * @param rawPassword 明文口令
     * @param roleCodes 角色码集合
     * @return 新账号视图
     * @throws IdentityProblem 邮箱已被占用或角色不存在
     */
    AdminUserView createUser(
            String email,
            String displayName,
            String rawPassword,
            Set<String> roleCodes
    );

    /**
     * 按邮箱读取口令校验所需凭据。
     *
     * @param email 登录邮箱
     * @return 凭据；账号不存在或不是自有口令账号时为空
     */
    Optional<AdminCredentials> findCredentials(String email);

    /**
     * 按标识读取工作区内的账号。
     *
     * <p>工作区为空（本地运营会话）时恒为空，本地账号与其它工作区的账号都不可见。</p>
     *
     * @param workspaceId 当前会话绑定的工作区；本地运营会话为空
     * @param userId      账号标识
     * @return 账号视图
     */
    Optional<AdminUserView> findUser(UUID workspaceId, UUID userId);

    /**
     * 分页查询账号。
     *
     * @param workspaceId 当前会话绑定的工作区；本地运营会话为空
     * @param offset 起始偏移
     * @param limit 最大条数
     * @param keyword 邮箱或名称关键字，可为空
     * @return 账号视图列表
     */
    List<AdminUserView> listUsers(UUID workspaceId, int offset, int limit, String keyword);

    /**
     * 统计符合条件的账号数量。
     *
     * @param workspaceId 当前会话绑定的工作区；本地运营会话为空
     * @param keyword 邮箱或名称关键字，可为空
     * @return 账号数量
     */
    long countUsers(UUID workspaceId, String keyword);

    /**
     * 修改账号展示名称。
     *
     * @param workspaceId 当前会话绑定的工作区
     * @param userId 账号标识
     * @param displayName 新展示名称
     * @return 更新后的账号视图
     * @throws IdentityProblem 账号不存在
     */
    AdminUserView renameUser(UUID workspaceId, UUID userId, String displayName);

    /**
     * 修改账号状态。
     *
     * @param workspaceId 当前会话绑定的工作区
     * @param userId 账号标识
     * @param status 新状态
     * @return 更新后的账号视图
     * @throws IdentityProblem 账号不存在
     */
    AdminUserView changeStatus(UUID workspaceId, UUID userId, UserStatus status);

    /**
     * 整体替换账号角色。
     *
     * <p>只替换本地分配：平台派生的管理员角色不在这里增删。</p>
     *
     * @param workspaceId 当前会话绑定的工作区
     * @param userId 账号标识
     * @param roleCodes 新角色码集合
     * @return 更新后的账号视图
     * @throws IdentityProblem 账号不存在或角色不存在
     */
    AdminUserView changeRoles(UUID workspaceId, UUID userId, Set<String> roleCodes);

    /**
     * 把平台 Launch 身份映射为本地影子账号。
     *
     * <p>平台影子账号必须绑定工作区，且该工作区必须与其平台实例映射一致；调用方只能传入
     * 由已认证 Launch 解析出的工作区，不能来自请求参数。</p>
     *
     * @param launch 已验证的 Launch 身份
     * @param workspaceId Launch 映射出的本地工作区标识
     * @return 本地账号视图
     */
    AdminUserView upsertPlatformUser(PlatformLaunchIdentity launch, UUID workspaceId);

    /**
     * 记录一次成功登录时间。
     *
     * @param userId 账号标识
     */
    void recordLogin(UUID userId);

    /**
     * 列出全部本地角色。
     *
     * @return 角色视图列表
     */
    List<AdminRoleView> listRoles();
}
