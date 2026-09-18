package app.runlume.admin.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 按完整平台边界读取本地 workspace 映射的只读入口。
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
     * @return 映射视图列表
     */
    List<WorkspaceView> list(int offset, int limit);

    /**
     * 统计映射数量。
     *
     * @return 映射数量
     */
    long count();
}
