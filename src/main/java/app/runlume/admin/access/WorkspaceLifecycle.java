package app.runlume.admin.access;

import java.util.Optional;
import java.util.UUID;

/**
 * 平台生命周期命令的幂等入口。
 *
 * <p>相同调用方、路由、幂等键和请求摘要返回第一次处理结果；相同幂等键绑定不同摘要返回
 * {@code IDEMPOTENCY_CONFLICT}。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:00
 */
public interface WorkspaceLifecycle {

    /**
     * 开通实例：创建本地 workspace 并建立平台映射。
     *
     * @param request 开通请求
     * @param idempotencyKey 平台提供的幂等键
     * @return 生命周期操作结果
     * @throws WorkspaceProblem 幂等冲突或平台实例标识已被占用
     */
    LifecycleOperationView provision(ProvisionRequest request, String idempotencyKey);

    /**
     * 暂停实例。
     *
     * @param externalInstanceId 不透明实例标识
     * @param idempotencyKey 平台提供的幂等键
     * @return 生命周期操作结果
     * @throws WorkspaceProblem workspace 不存在
     */
    LifecycleOperationView suspend(String externalInstanceId, String idempotencyKey);

    /**
     * 恢复实例。
     *
     * @param externalInstanceId 不透明实例标识
     * @param idempotencyKey 平台提供的幂等键
     * @return 生命周期操作结果
     * @throws WorkspaceProblem workspace 不存在
     */
    LifecycleOperationView resume(String externalInstanceId, String idempotencyKey);

    /**
     * 注销实例并撤销访问。
     *
     * @param externalInstanceId 不透明实例标识
     * @param idempotencyKey 平台提供的幂等键
     * @return 生命周期操作结果
     * @throws WorkspaceProblem workspace 不存在
     */
    LifecycleOperationView deprovision(String externalInstanceId, String idempotencyKey);

    /**
     * 查询异步操作结果。
     *
     * @param operationId 操作标识
     * @return 操作结果
     */
    Optional<LifecycleOperationView> findOperation(UUID operationId);

    /**
     * 平台开通命令的入参。
     *
     * @param platformAccountId 平台 Account 标识
     * @param platformAppInstanceId 平台 AppInstance 标识
     * @param moduleKey 平台模块 Key
     * @param moduleVersion 平台模块版本
     */
    record ProvisionRequest(
            UUID platformAccountId,
            UUID platformAppInstanceId,
            String moduleKey,
            String moduleVersion
    ) {
    }
}
