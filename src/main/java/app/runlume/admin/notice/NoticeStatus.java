package app.runlume.admin.notice;

/**
 * 公告状态。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:20
 */
public enum NoticeStatus {

    /** 草稿，可编辑。 */
    DRAFT,

    /** 已发布，只读。 */
    PUBLISHED,

    /** 已归档，只读。 */
    ARCHIVED
}
