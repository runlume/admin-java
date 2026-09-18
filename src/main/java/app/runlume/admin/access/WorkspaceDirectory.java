package app.runlume.admin.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 读取工作区的只读入口。
 *
 * <p>平台映射视图按完整平台边界读取，因此只包含 {@code PLATFORM} 工作区；本系统自建的
 * {@code LOCAL} 工作区没有平台边界，只通过 {@link #statusOf(java.util.UUID)} 暴露状态，
 * 供本地工作区会话逐请求复验。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:00
 */
public interface WorkspaceDirectory {

    /**
     * 按平台 AppInstance 读取映射。
     *
     * @param platformAppInstanceId 平台 AppInstance 标识
     * @return 映射视图
     */
    Optional<WorkspaceView> findByAppInstance(UUID platformAppInstanceId);

    /**
     * 按完整平台边界读取映射，供会话建立与逐请求复验使用。
     *
     * @param platformAccountId 平台 Account 标识
     * @param platformAppInstanceId 平台 AppInstance 标识
     * @return 映射视图
     */
    Optional<WorkspaceView> find(UUID platformAccountId, UUID platformAppInstanceId);

    /**
     * 按对平台暴露的不透明实例标识读取映射。
     *
     * @param externalInstanceId 不透明实例标识
     * @return 映射视图
     */
    Optional<WorkspaceView> findByExternalInstanceId(String externalInstanceId);

    /**
     * 分页列出映射。
     *
     * @param offset 起始偏移
     * @param limit 最大条数
     * @return 映射视图列表；只含平台映射工作区
     */
    List<WorkspaceView> list(int offset, int limit);

    /**
     * 统计映射数量。
     *
     * @return 映射数量；只统计平台映射工作区
     */
    long count();

    /**
     * 按本地工作区标识读取状态。
     *
     * @param workspaceId 本地工作区标识
     * @return 工作区状态；不存在时为空
     */
    Optional<WorkspaceStatus> statusOf(UUID workspaceId);
}
