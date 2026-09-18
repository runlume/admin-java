package app.runlume.admin.access.identity.infrastructure.web;

import app.runlume.admin.access.identity.infrastructure.PlatformConnectionProbe;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录说明页使用的平台联机状态端点。
 *
 * <p>响应只能包含布尔状态，不返回平台地址、状态码、异常文本或任何部署信息，并固定
 * {@code Cache-Control: no-store}。本端点匿名开放，但只说明“后端能解析平台公钥”，
 * 不代表浏览器已登录或用户已获实例授权。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 10:50
 */
@RestController
public class PlatformConnectionController {

    private final PlatformConnectionProbe probe;

    /**
     * 创建联机状态控制器。
     *
     * @param probe 联机探针
     */
    public PlatformConnectionController(PlatformConnectionProbe probe) {
        this.probe = probe;
    }

    /**
     * 读取平台联机状态。
     *
     * @return 只含布尔字段的响应
     */
    @GetMapping("/api/v1/platform-connection")
    public ResponseEntity<PlatformConnectionResponse> connection() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PlatformConnectionResponse(probe.connected()));
    }

    /**
     * 平台联机状态。
     *
     * @param connected 后端能否在超时内解析平台运行面公钥
     */
    public record PlatformConnectionResponse(boolean connected) {
    }
}
