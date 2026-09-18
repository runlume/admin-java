# 平台开放能力与 SDK 接口

本文说明 `platform-integration-sdk-java` 提供哪些开放能力、入口在哪、接入前要满足什么前置。
与平台交互的协议细节见 [平台接入契约](platform-integration.md)；本文只讲"有什么、怎么用"。

## 1. 为什么这份文档在业务仓库里

SDK 是**只给二进制**的交付物：不发布源码 JAR，也没有随制品发布 javadoc，目的是让源码不随制品外流。
因此接入方拿到的只有一个 jar，没有可查的参考文档——只能在接入仓库里维护一份能力与入口清单。

本文刻意**不做逐字段的 API 参考**。那种文档必须随 SDK 版本演进，放在接入方仓库里必然滞后；
需要字段级说明时以平台发布的契约和 SDK 的 javadoc 为准。这里只回答三个问题：
这个能力能做什么、入口是哪个类型和方法、调用前需要什么。

## 2. 取得方式

```groovy
implementation 'app.runlume.platform:platform-integration-sdk-java:1.0.0'
```

- 制品来自 Runlume Nexus：`https://nexus.runlume.app/repository/maven-public/`
- 最低 Java 25；SDK 依赖 Jackson 3、Jakarta Annotation 与 Nimbus JOSE JWT
- `META-INF/runlume/platform-integration-sdk-compatibility.json` 里记录了版本、各契约摘要与
  Operation 兼容矩阵，升级前可比对

## 3. 能力总览

SDK 覆盖 9 组平台契约。**能调用哪些不由 SDK 决定**：平台按已发布的 Manifest 推导出模块可授予的
Scope，未声明的能力不会授予，调用会失败。

| 能力 | SDK 包 | 能做什么 | 调用前需要 |
| --- | --- | --- | --- |
| 身份 | `sdk.identity` | 交换 Launch Code、申请实例服务 Token、读取运行时公钥 | 模块服务身份（client_credentials） |
| 资源与用量 | `sdk.resource` | 查询实例权益、上报真实用量 | 实例服务 Token |
| 平台 AI | `sdk.resource` / `sdk.ai` | 调用平台托管的模型（普通与流式） | 实例服务 Token + 平台 AI 额度 |
| Web 能力 | `sdk.web` | Web 搜索、网页内容抓取 | 实例服务 Token |
| 文件 | `sdk.file` | 上传、列举、读取、下载、删除运行时文件 | 实例服务 Token |
| 统一通知 | `sdk.notification` | 向当前实例发通知、读自己的收件箱与未读数 | 实例服务 Token |
| 组织目录 | `sdk.organization` | 拉取组织快照与增量、读取当前修订号 | 已订阅平台组织目录 |
| 跨系统集成 | `sdk.integration` | 查询已授权能力、调用 Provider、发布事件、领取 Webhook 密钥 | 已发布的 Capability 契约 + ACTIVE Binding |
| 版本升级 | `sdk.lifecycle` | 驱动实例版本升级、查询升级操作 | 声明了升级路径的模块 |
| 接入方身份 | `sdk.provider` | 接入方机器身份与上下文 | IntegrationProvider 场景 |

## 4. 各能力的接入要点

### 4.1 身份

入口是 `RuntimeIdentityClient`，它把"交换 Launch Code"和"申请实例服务 Token"收敛成两个方法，
并负责验证 Issuer、Audience、签名、有效期与模块边界。它**不申请、不缓存任何凭据**——
模块服务 Token 必须由调用方自己取得，admin-java 的做法见 `ModuleServiceTokenProvider`。

### 4.2 资源、AI、Web、文件、通知

这五类的共同点：都按实例授权，**Token 里的实例就是唯一租户上下文**，请求体里的同名字段不能扩大范围。

- 权益是服务端事实：功能能不能用由 FEATURE 决定，不要用前端开关代替服务端判断；
- 用量要上报真实值，且写接口必须带稳定幂等键，重试不能重复扣量；
- 平台 AI 由平台完成额度预占与结算；启用自定义 AI 时调用不经过平台，也就不扣平台 Token；
- 文件的上传与下载都用短期预签名地址，平台角色不自动获得租户文件的读取权；
- 通知固定创建站内消息，邮件按权限与额度可选。

### 4.3 组织目录

`OrganizationDirectoryClient` 返回的是投影：`Snapshot` 带 `highWatermark`，
`ChangeBatch` 带 `currentRevision` 和 `isContinuousAfter(...)`。

**增量的连续性必须自己判断**：发现不连续就重建，不能跳过或覆盖更高 revision。
远程调用放在本地事务之外，拉取成功后再用一个本地事务原子推进投影与检查点。

### 4.4 跨系统集成

`CapabilityInvocationClient` 只负责按 `capabilityKey + majorVersion + tailPath` 发起调用；
授权判断全在平台：同 Account、ACTIVE Binding、Scope、实例状态与权益都由平台逐次校验。
写能力必须带幂等键。事件方向则是本系统先写 Outbox，再发布并验签重试。

### 4.5 版本升级与接入方身份

实例版本升级的命令方向是**平台 → 本系统**：本系统实现升级入口并幂等执行，跟着自己的数据库迁移
走。`sdk.lifecycle` 与 `sdk.provider` 的生成 Client 主要服务平台侧与工具链，
普通业务系统通常不直接调用，是否需要由平台按模块决定。

## 5. 手写门面与工具类型

SDK 里除按契约生成的 Client 外，还有一组跨契约的手写类型。它们才是接入方真正长期依赖的 API：

| 类型 | 作用 |
| --- | --- |
| `sdk.identity.RuntimeIdentityClient` | Launch 交换与实例服务 Token 申请，含稳定失败分类 |
| `sdk.organization.OrganizationDirectoryClient` | 组织快照、增量与修订号 |
| `sdk.integration.CapabilityInvocationClient` | 按契约调用已授权能力 |
| `sdk.ai.AiStreamingClient` | 流式 AI 调用，按增量回调 |
| `sdk.security.PlatformJwtVerifier` | 校验平台运行面签发的 RS256 Token |
| `sdk.security.WebhookSignatureVerifier` | 校验平台 Webhook 的 HMAC 签名 |
| `sdk.PlatformProblem` | 解析 `application/problem+json` 响应 |
| `sdk.DeploymentLicenseRuntimeDependency` | 声明私有化交付的 License 运行期依赖 |

主要入口签名：

```java
// 身份
RuntimeIdentityClient(ApiClient apiClient, String expectedModuleId,
                      PlatformJwtVerifier launchVerifier,
                      PlatformJwtVerifier instanceTokenVerifier)
VerifiedLaunch exchangeLaunchGrant(String moduleBearerToken, String code)
String createInstanceServiceToken(String moduleBearerToken, UUID expectedAccountId,
                                  UUID expectedAppInstanceId, Set<String> scopes)

// 组织目录
Snapshot snapshot(String bearerToken)
ChangeBatch changes(String bearerToken, long afterRevision)
long revision(String bearerToken)

// 跨系统集成
Object get(String capabilityKey, int majorVersion, String tailPath,
           Map<String, String> queryParameters) throws ApiException
Object post(String capabilityKey, int majorVersion, String tailPath,
            Map<String, String> queryParameters, String idempotencyKey, Object body) throws ApiException
Object delete(String capabilityKey, int majorVersion, String tailPath,
              Map<String, String> queryParameters, String idempotencyKey) throws ApiException

// 流式 AI
AiChatCompletion stream(String bearerToken, String idempotencyKey,
                        AiChatCompletionRequest request, Consumer<String> deltaConsumer)

// 安全
PlatformJwtVerifier(String issuer, String audience, JwksSource jwksSource,
                    Clock clock, Duration clockSkew)
static PlatformJwtVerifier.JwksSource httpJwksSource(URI jwksUri)
VerifiedToken verify(String token, String expectedTokenUse, Set<String> requiredScopes)
static boolean verify(String base64UrlSecret, String timestampHeader, String signatureHeader,
                      byte[] rawPayload, Instant now, Duration tolerance)
```

## 6. 稳定失败分类

SDK 不把上游响应体、Token 或 Claims 放进异常，只给稳定分类，接入方据此决定重试与提示：

| 类型 | 取值 |
| --- | --- |
| `RuntimeIdentityClient.LaunchReason` | `INVALID`、`EXPIRED`、`USED`、`MODULE_MISMATCH`、`UNAVAILABLE` |
| `RuntimeIdentityClient.TokenReason` | `REJECTED`、`UNAVAILABLE` |
| `PlatformJwtVerifier.Reason` | 算法、签名、Issuer、Audience、有效期、Token Use、Scope、身份等逐项分类 |
| `AiStreamingClient.Reason` | `HTTP_ERROR`、`TRANSPORT_ERROR`、`PROTOCOL_ERROR`、`INCOMPLETE_STREAM`、`CALLBACK_FAILED`、`INTERRUPTED` |
| `PlatformProblem` | `status`、`code`、`detail`、`traceId` |

映射到本系统错误码的规则见 [接口契约](api.md#6-错误码)：
平台拒绝 → `400`，平台不可用 → `503`，不把上游细节透给浏览器。

## 7. 与 admin-java 当前实现的关系

| 能力 | 本仓库状态 |
| --- | --- |
| 身份（Launch 换码） | 已接入：`SdkPlatformLaunchGateway` |
| 身份（模块服务 Token） | 已实现：`ModuleServiceTokenProvider` |
| 运行时 Token 验签 | 已接入：`PlatformJwtVerifier` |
| 联机探针 | 已实现：`PlatformConnectionProbe`（只探测 JWKS，不用 SDK） |
| 生命周期入站 | 已实现：生命周期端点，鉴权由独立安全链完成，不经过 SDK |
| 资源、AI、Web、文件、通知、组织目录、跨系统集成 | **未接入**，属于完整接入 |

## 8. 接入顺序建议

不要在模板里预留空实现。按真实用例逐个启用：

1. **先最小接入**：统一开通与登录，也就是本仓库现在的状态；
2. 出现"功能要不要给人用"的问题时，接资源权益；
3. 需要 AI、搜索或文件时，接对应能力，并同时决定用量上报与失败提示；
4. 需要与同 Account 的其它 SaaS 互通时，再走 Binding 与 Capability；
5. 需要统一提醒时，接通知，并按业务事务写 Outbox。

每一步都要补齐负向测试：伪造租户、跨实例、过期 Token、重复幂等键、额度耗尽。
