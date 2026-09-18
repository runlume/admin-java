package app.runlume.admin.access.identity.infrastructure.web;

import app.runlume.admin.access.WorkspaceDirectory;
import app.runlume.admin.access.WorkspaceProblem;
import app.runlume.admin.access.WorkspaceView;
import app.runlume.admin.access.identity.AdminIdentity;
import app.runlume.admin.access.identity.AdminUserView;
import app.runlume.admin.access.identity.LocalAccountProperties;
import app.runlume.admin.access.identity.PlatformLaunchGateway;
import app.runlume.admin.access.identity.PlatformLaunchIdentity;
import app.runlume.admin.access.identity.infrastructure.security.LocalSessionEstablisher;
import app.runlume.admin.access.observability.AdminAuditLog;
import app.runlume.admin.access.observability.AuditEntry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 接收平台一次性 Launch Code 并建立本地会话。
 *
 * <p>平台以跨站表单把 Code 提交到本入口，因此本端点按契约豁免 CSRF。Code 只出现在表单
 * Body 中，不写入日志、审计或会话；校验由平台集成 SDK 完成，本控制器只负责把已验证身份
 * 映射为本地账号、检查 workspace 状态并建立本地会话。</p>
 *
 * <p>会话寿命取本地配置的绝对时长，不再压缩到 Context Token 的 {@code exp}：短票据只证明
 * 进入时点，之后由 {@code SessionValidationFilter} 按声明的校验窗口持续确认成员仍然有效，
 * 撤销窗口因此由窗口而非会话时长决定。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:50
 */
@RestController
public class LaunchController {

    private static final Pattern SAFE_ACTION_PATH = Pattern.compile("^/(?!/)[^\\\\?#]*$");
    private static final String DEFAULT_ACTION_PATH = "/";

    private final PlatformLaunchGateway launchGateway;
    private final AdminIdentity identity;
    private final WorkspaceDirectory workspaces;
    private final LocalSessionEstablisher sessionEstablisher;
    private final LocalAccountProperties properties;
    private final AdminAuditLog auditLog;
    private final Clock clock;

    /**
     * 创建 Launch 控制器。
     *
     * @param launchGateway Launch 交换端口
     * @param identity 本地身份入口
     * @param workspaces 工作区读取入口
     * @param sessionEstablisher 会话建立器
     * @param properties 本地账号配置
     * @param auditLog 审计入口
     * @param clock 判定时间源
     */
    public LaunchController(
            PlatformLaunchGateway launchGateway,
            AdminIdentity identity,
            WorkspaceDirectory workspaces,
            LocalSessionEstablisher sessionEstablisher,
            LocalAccountProperties properties,
            AdminAuditLog auditLog,
            Clock clock
    ) {
        this.launchGateway = launchGateway;
        this.identity = identity;
        this.workspaces = workspaces;
        this.sessionEstablisher = sessionEstablisher;
        this.properties = properties;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * 交换 Launch Code、建立本地会话并跳转到站内目标路径。
     *
     * @param code 平台签发的一次性 Launch Code
     * @param request 当前请求
     * @param response 当前响应
     * @return 303 跳转
     */
    @PostMapping(
            path = "${admin.platform.launch-path:/launch}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public ResponseEntity<Void> launch(
            @RequestParam("code") String code,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PlatformLaunchIdentity launch = launchGateway.exchange(code);
        WorkspaceView workspace = workspaces
                .findByAppInstance(launch.platformAppInstanceId())
                .filter(WorkspaceView::acceptsNewSession)
                .orElseThrow(() -> WorkspaceProblem.of(
                        WorkspaceProblem.Code.WORKSPACE_NOT_ACTIVE
                ));
        AdminUserView user = identity.upsertPlatformUser(launch, workspace.id());
        Instant expiresAt = Instant.now(clock).plus(properties.sessionTtl());
        sessionEstablisher.establish(
                user,
                workspace,
                launch.membershipRevision(),
                expiresAt,
                request,
                response
        );
        auditLog.record(new AuditEntry(
                AuditEntry.ActorType.PLATFORM,
                launch.platformUserId().toString(),
                "user.launch",
                "user",
                user.id().toString(),
                workspace.id(),
                AuditEntry.Outcome.SUCCESS
        ));
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(actionPath(launch.actionPath()))
                .build();
    }

    private static URI actionPath(String actionPath) {
        if (actionPath != null && SAFE_ACTION_PATH.matcher(actionPath).matches()) {
            return URI.create(actionPath);
        }
        return URI.create(DEFAULT_ACTION_PATH);
    }
}
