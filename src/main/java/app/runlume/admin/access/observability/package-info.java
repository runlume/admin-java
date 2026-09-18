/**
 * 跨切面可观测性：服务端请求关联标识与非敏感审计记录。
 *
 * <p>业务域只能通过本 Named Interface 读取关联标识、写入审计，不能直接访问
 * {@code infrastructure} 下的持久化实现。</p>
 */
@NamedInterface("observability")
package app.runlume.admin.access.observability;

import org.springframework.modulith.NamedInterface;
