package app.runlume.admin.access.identity;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 本地角色的只读视图。
 *
 * @param id 角色标识
 * @param code 稳定角色码
 * @param name 展示名称
 * @param description 说明，可为空
 * @param builtIn 是否内置角色
 * @param permissionCodes 权限码原始集合
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:50
 */
public record AdminRoleView(
        UUID id,
        String code,
        String name,
        String description,
        boolean builtIn,
        Set<String> permissionCodes
) {

    /**
     * 固化必填字段。
     */
    public AdminRoleView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        permissionCodes = Set.copyOf(permissionCodes);
    }
}
