package app.runlume.admin.notice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 公告读写入口。
 *
 * <p>所有读写都以工作区为可见范围：平台公共公告对全部会话可见，实例公告只对所属工作区
 * 可见。工作区标识只能来自已认证会话，不接受请求参数覆盖。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:20
 */
public interface NoticeDirectory {

    /**
     * 分页查询可见公告。
     *
     * @param offset 起始偏移
     * @param limit 最大条数
     * @param status 状态过滤，可为空
     * @param workspaceId 当前会话工作区，可为空
     * @return 公告列表
     */
    List<Notice> list(int offset, int limit, NoticeStatus status, UUID workspaceId);

    /**
     * 统计可见公告数量。
     *
     * @param status 状态过滤，可为空
     * @param workspaceId 当前会话工作区，可为空
     * @return 公告数量
     */
    long count(NoticeStatus status, UUID workspaceId);

    /**
     * 读取可见公告。
     *
     * @param id 公告标识
     * @param workspaceId 当前会话工作区，可为空
     * @return 公告
     */
    Optional<Notice> find(UUID id, UUID workspaceId);

    /**
     * 创建草稿公告。
     *
     * @param title 标题
     * @param body 正文
     * @param workspaceId 归属工作区，可为空表示公共公告
     * @param authorId 创建人本地账号标识
     * @return 新公告
     */
    Notice create(String title, String body, UUID workspaceId, UUID authorId);

    /**
     * 修改草稿公告。
     *
     * @param id 公告标识
     * @param workspaceId 当前会话工作区，可为空
     * @param title 新标题
     * @param body 新正文
     * @return 更新后的公告
     * @throws NoticeProblem 公告不可见或状态不允许修改
     */
    Notice update(UUID id, UUID workspaceId, String title, String body);

    /**
     * 变更公告状态。
     *
     * @param id 公告标识
     * @param workspaceId 当前会话工作区，可为空
     * @param status 目标状态
     * @return 更新后的公告
     * @throws NoticeProblem 公告不可见
     */
    Notice changeStatus(UUID id, UUID workspaceId, NoticeStatus status);
}
