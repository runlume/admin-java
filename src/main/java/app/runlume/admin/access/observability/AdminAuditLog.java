package app.runlume.admin.access.observability;

/**
 * 追加非敏感审计事实的公开入口。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:40
 */
public interface AdminAuditLog {

    /**
     * 追加一条审计事实。
     *
     * <p>实现自行读取当前请求的关联标识，并在没有请求上下文时继续工作。</p>
     *
     * @param entry 非敏感审计事实
     */
    void record(AuditEntry entry);
}
