package app.runlume.admin.access.infrastructure.web;

import app.runlume.admin.access.LifecycleOperationView;
import app.runlume.admin.access.WorkspaceLifecycle;
import app.runlume.admin.access.WorkspaceProblem;
import app.runlume.admin.access.observability.AdminAuditLog;
import app.runlume.admin.access.observability.AuditEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 平台生命周期入站端点。
 *
 * <p>身份与 Scope 由独立的安全链在进入本控制器前完成校验；控制器只做参数到领域命令的
 * 转换，并把全部写入委托给幂等的 {@link WorkspaceLifecycle}。请求体中的 Account 与实例
 * 声明只在首次建档时使用，不能作为普通业务请求的租户凭据。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:00
 */
@RestController
@RequestMapping("/integration/v1")
public class LifecycleController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final WorkspaceLifecycle lifecycle;
    private final AdminAuditLog auditLog;

    /**
     * 创建生命周期控制器。
     *
     * @param lifecycle 生命周期入口
     * @param auditLog 审计入口
     */
    public LifecycleController(WorkspaceLifecycle lifecycle, AdminAuditLog auditLog) {
        this.lifecycle = lifecycle;
        this.auditLog = auditLog;
    }

    /**
     * 开通实例。
     *
     * @param idempotencyKey 平台幂等键
     * @param body 开通请求
     * @return 操作结果
     */
    @PostMapping("/app-instances")
    public ResponseEntity<ProvisionResponse> provision(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody ProvisionRequest body
    ) {
        LifecycleOperationView operation = lifecycle.provision(
                new WorkspaceLifecycle.ProvisionRequest(
                        body.accountId(),
                        body.appInstanceId(),
                        body.moduleKey(),
                        body.moduleVersion()
                ),
                idempotencyKey
        );
        auditRecord("instance.provision", operation, AuditEntry.Outcome.SUCCESS);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ProvisionResponse(
                operation.id().toString(),
                operation.externalInstanceId(),
                operation.state()
        ));
    }

    /**
     * 查询异步操作结果。
     *
     * @param operationId 操作标识
     * @return 操作结果
     */
    @GetMapping("/operations/{operationId}")
    public OperationResponse operation(@PathVariable UUID operationId) {
        return lifecycle.findOperation(operationId)
                .map(OperationResponse::from)
                .orElseThrow(() -> WorkspaceProblem.of(
                        WorkspaceProblem.Code.OPERATION_NOT_FOUND
                ));
    }

    /**
     * 暂停实例。
     *
     * @param externalInstanceId 不透明实例标识
     * @param idempotencyKey 平台幂等键
     * @return 操作结果
     */
    @PostMapping("/app-instances/{externalInstanceId}/suspend")
    public OperationResponse suspend(
            @PathVariable String externalInstanceId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey
    ) {
        LifecycleOperationView operation = lifecycle.suspend(externalInstanceId, idempotencyKey);
        auditRecord("instance.suspend", operation, AuditEntry.Outcome.SUCCESS);
        return OperationResponse.from(operation);
    }

    /**
     * 恢复实例。
     *
     * @param externalInstanceId 不透明实例标识
     * @param idempotencyKey 平台幂等键
     * @return 操作结果
     */
    @PostMapping("/app-instances/{externalInstanceId}/resume")
    public OperationResponse resume(
            @PathVariable String externalInstanceId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey
    ) {
        LifecycleOperationView operation = lifecycle.resume(externalInstanceId, idempotencyKey);
        auditRecord("instance.resume", operation, AuditEntry.Outcome.SUCCESS);
        return OperationResponse.from(operation);
    }

    /**
     * 注销实例并立即撤销访问。
     *
     * @param externalInstanceId 不透明实例标识
     * @param idempotencyKey 平台幂等键
     * @return 操作结果
     */
    @DeleteMapping("/app-instances/{externalInstanceId}")
    public OperationResponse deprovision(
            @PathVariable String externalInstanceId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey
    ) {
        LifecycleOperationView operation = lifecycle.deprovision(
                externalInstanceId,
                idempotencyKey
        );
        auditRecord("instance.deprovision", operation, AuditEntry.Outcome.SUCCESS);
        return OperationResponse.from(operation);
    }

    private void auditRecord(
            String action,
            LifecycleOperationView operation,
            AuditEntry.Outcome outcome
    ) {
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.PLATFORM,
                null,
                action,
                "workspace",
                operation.externalInstanceId(),
                operation.platformAppInstanceId(),
                outcome
        ));
    }

    /**
     * 开通请求。
     *
     * @param accountId 平台 Account 标识
     * @param appInstanceId 平台 AppInstance 标识
     * @param moduleKey 平台模块 Key
     * @param moduleVersion 平台模块版本
     */
    public record ProvisionRequest(
            @NotNull UUID accountId,
            @NotNull UUID appInstanceId,
            @NotBlank @Size(max = 120) String moduleKey,
            @NotBlank @Size(max = 64) String moduleVersion
    ) {
    }

    /**
     * 开通响应。
     *
     * @param operationId 操作标识
     * @param externalInstanceId 不透明实例标识
     * @param state 操作状态
     */
    public record ProvisionResponse(
            String operationId,
            String externalInstanceId,
            String state
    ) {
    }

    /**
     * 操作结果响应。
     *
     * @param operationId 操作标识
     * @param command 命令名
     * @param state 操作状态
     * @param externalInstanceId 不透明实例标识
     * @param failureCode 稳定失败码
     */
    public record OperationResponse(
            String operationId,
            String command,
            String state,
            String externalInstanceId,
            String failureCode
    ) {

        /**
         * 由操作视图构造响应。
         *
         * @param operation 操作视图
         * @return 操作结果
         */
        public static OperationResponse from(LifecycleOperationView operation) {
            return new OperationResponse(
                    operation.id().toString(),
                    operation.command(),
                    operation.state(),
                    operation.externalInstanceId(),
                    operation.failureCode()
            );
        }
    }
}
