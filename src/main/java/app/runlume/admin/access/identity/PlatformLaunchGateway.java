package app.runlume.admin.access.identity;

/**
 * 消费一次性 Launch Code 并交换为已验证身份的出站端口。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 09:50
 */
public interface PlatformLaunchGateway {

    /**
     * 交换一次性的 Launch Code。
     *
     * @param launchCode 浏览器提交的一次性 Launch Code
     * @return 已验证的 Launch 身份最小投影
     * @throws IdentityProblem 平台拒绝、不可用或 Token 校验失败
     */
    PlatformLaunchIdentity exchange(String launchCode);
}
