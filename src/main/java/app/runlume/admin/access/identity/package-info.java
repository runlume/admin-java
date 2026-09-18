/**
 * 身份与会话：平台 Launch 信任链、本地账号、角色权限与本地会话。
 *
 * <p>业务域只能通过本 Named Interface 取得已认证的 {@link app.runlume.admin.access.identity.AdminSessionPrincipal}
 * 与角色权限结论，不能直接访问 {@code infrastructure} 下的安全链、持久化或 SDK Adapter。</p>
 */
@NamedInterface("identity")
package app.runlume.admin.access.identity;

import org.springframework.modulith.NamedInterface;
