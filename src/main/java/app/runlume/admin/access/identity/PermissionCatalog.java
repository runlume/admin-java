package app.runlume.admin.access.identity;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 后台权限码目录与通配匹配规则。
 *
 * <p>权限码约定与 admin-design 前端一致：{@code *} 表示全部，
 * {@code <命名空间>.<资源>.*} 表示该资源下全部，其余精确匹配。界面按原始权限码过滤菜单与
 * 按钮，服务端把 {@code *} 与资源通配展开成具体码后再写入 Spring Security 授权集合，
 * 避免出现“前端可见、后端拒绝”的偏差。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:50
 */
public final class PermissionCatalog {

    /** 全部权限。 */
    public static final String ALL = "*";

    /** 当前部署的权限命名空间，必须与平台登记的模块标识一致。 */
    public static final String NAMESPACE = "example.admin";

    /** 查看本人资料。 */
    public static final String PROFILE_VIEW = NAMESPACE + ".profile.view";

    /** 查看工作区成员列表与详情。 */
    public static final String MEMBER_VIEW = NAMESPACE + ".member.view";

    /** 修改成员资料与本地角色。 */
    public static final String MEMBER_UPDATE = NAMESPACE + ".member.update";

    /** 启用或禁用成员。 */
    public static final String MEMBER_DISABLE = NAMESPACE + ".member.disable";

    /** 查看角色与权限目录。 */
    public static final String ROLE_VIEW = NAMESPACE + ".role.view";

    /** 查看审计记录。 */
    public static final String AUDIT_VIEW = NAMESPACE + ".audit.view";

    /** 查看公告。 */
    public static final String NOTICE_VIEW = NAMESPACE + ".notice.view";

    /** 编辑、发布与归档公告。 */
    public static final String NOTICE_MANAGE = NAMESPACE + ".notice.manage";

    /** 查看平台接入状态与工作区。 */
    public static final String PLATFORM_VIEW = NAMESPACE + ".platform.view";

    private static final Pattern CODE_PATTERN = Pattern.compile(
            "^(\\*|[a-z0-9]+(\\.[a-z0-9]+){2,}(\\.\\*)?)$"
    );

    private static final List<PermissionDefinition> DEFINITIONS = List.of(
            new PermissionDefinition(PROFILE_VIEW, "profile", "查看本人资料"),
            new PermissionDefinition(MEMBER_VIEW, "member", "查看工作区成员"),
            new PermissionDefinition(MEMBER_UPDATE, "member", "编辑成员与本地角色"),
            new PermissionDefinition(MEMBER_DISABLE, "member", "启用或禁用成员"),
            new PermissionDefinition(ROLE_VIEW, "role", "查看角色"),
            new PermissionDefinition(AUDIT_VIEW, "audit", "查看审计"),
            new PermissionDefinition(NOTICE_VIEW, "notice", "查看公告"),
            new PermissionDefinition(NOTICE_MANAGE, "notice", "管理公告"),
            new PermissionDefinition(PLATFORM_VIEW, "platform", "查看平台接入")
    );

    private PermissionCatalog() {
    }

    /**
     * 返回代码内置的权限目录。
     *
     * @return 权限定义，顺序稳定
     */
    public static List<PermissionDefinition> definitions() {
        return DEFINITIONS;
    }

    /**
     * 判断权限码是否符合目录格式。
     *
     * @param code 待校验权限码
     * @return 合法时为 true
     */
    public static boolean isValidCode(String code) {
        return code != null && CODE_PATTERN.matcher(code).matches();
    }

    /**
     * 判断原始权限集合是否满足指定具体权限码。
     *
     * @param granted 已授予的原始权限码
     * @param required 需要的具体权限码
     * @return 满足时为 true
     */
    public static boolean grants(Collection<String> granted, String required) {
        for (String code : granted) {
            if (ALL.equals(code) || code.equals(required)) {
                return true;
            }
            if (code.endsWith(".*")
                    && required.startsWith(code.substring(0, code.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把通配权限展开为具体权限码，用于服务端授权集合。
     *
     * <p>返回集合不含 {@code *} 与 {@code 命名空间.资源.*} 本身，未登记的权限码原样保留，
     * 便于业务域自行扩展。</p>
     *
     * @param granted 已授予的原始权限码
     * @return 展开后的具体权限码
     */
    public static Set<String> expand(Collection<String> granted) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String code : granted) {
            if (ALL.equals(code)) {
                DEFINITIONS.forEach(definition -> expanded.add(definition.code()));
            } else if (code.endsWith(".*")) {
                String prefix = code.substring(0, code.length() - 1);
                DEFINITIONS.stream()
                        .map(PermissionDefinition::code)
                        .filter(candidate -> candidate.startsWith(prefix))
                        .forEach(expanded::add);
            } else {
                expanded.add(code);
            }
        }
        return Set.copyOf(expanded);
    }

    /**
     * 一条内置权限定义。
     *
     * @param code 稳定权限码
     * @param module 所属模块前缀
     * @param name 中文名称
     */
    public record PermissionDefinition(String code, String module, String name) {
    }
}
