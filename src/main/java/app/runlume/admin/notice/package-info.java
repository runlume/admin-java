/**
 * 示例业务域：公告。
 *
 * <p>本包演示业务域的标准位置与边界：与 {@code access} 平级，只通过
 * {@code access.identity} 与 {@code access.observability} 两个 Named Interface
 * 读取会话主体、写入审计，不直接依赖平台 SDK、jOOQ 平台映射表或安全链实现。</p>
 *
 * <p>复制模板时把本包替换为真实业务域，并保持所有业务表带 workspace 维度。</p>
 */
package app.runlume.admin.notice;
