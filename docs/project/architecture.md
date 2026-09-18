# 架构与边界

> 平台接入的接口契约见 [platform-integration.md](platform-integration.md)。
> 平台侧的完整接入标准由平台侧维护，本仓库不转引。

## 1. 定位

admin-java 是**业务系统后端**，不是平台控制面。它自己拥有进程、数据库和会话，通过薄适配层
`access` 与平台交换身份和生命周期事实，平台不跨库查询本系统的业务数据。

与平台的关系分两层：

- **本地层**：自有账号、角色、权限、会话。不接平台也能独立运行。
- **接入层**：平台 Launch 建会话、实例生命周期、联机探针。由 `access` 独占。

因此部署有两种模式，**并存**而不是二选一：

| 模式 | 开关 | 工作区来源 | 进入方式 |
| --- | --- | --- | --- |
| 本地自有 | `admin.platform.enabled=false` + `admin.local.registration-enabled=true` | 本系统自建的 `LOCAL` 工作区 | 本系统账号口令 |
| 平台接入 | `admin.platform.enabled=true` | 平台开通创建的 `PLATFORM` 工作区 | 平台 Launch |

平台接入打开时默认关闭自助注册（生产入口是平台 Launch），但本地账号体系仍然保留：本地账号
拥有自己的本地工作区并照常管理本工作区成员。两个模式的数据不互相可见：平台会话只看到平台
映射工作区，本地会话只看到本地工作区。

## 2. 包结构

```text
app.runlume.admin
├── access/                     平台集成 owning module
│   ├── WorkspaceStatus         工作区状态机
│   ├── WorkspaceView           平台实例 ↔ 平台来源工作区的映射
│   ├── WorkspaceDirectory      工作区只读端口：平台映射与本地工作区状态
│   ├── WorkspaceLifecycle      生命周期命令的幂等端口
│   ├── PlatformIntegrationProperties
│   ├── identity/               Named Interface "identity"
│   ├── observability/          Named Interface "observability"
│   └── infrastructure/         jOOQ 实现、生命周期入站、Problem 处理
└── notice/                     示例业务域（与 access 平级）
```

规则：

1. 业务域**只能** import `access.identity` 与 `access.observability` 的公开类型。
2. 业务域禁止 import `access.*.infrastructure`、平台 SDK 类型和 jOOQ 生成类型。
3. `access` 可以访问工作区映射表与平台 SDK；业务域不得自行读取平台 Token 或直接请求平台。
4. 由 `AdminModulesTests` 的 `ApplicationModules.verify()` 与源码 import 审查共同约束。

## 3. 身份与会话

两条链路最终走同一个 `LocalSessionEstablisher`，行为一致：先生成新的会话标识防御会话固定，
再写入只含派生身份的 `AdminSessionPrincipal`。

| 链路 | 身份来源 | 会话绝对过期 |
| --- | --- | --- |
| 本地登录 / 注册 | `admin_user`（BCrypt 口令） | `admin.local.session-ttl` |
| 平台 Launch | 平台 Context Token 验证后的映射账号 | `admin.local.session-ttl`，撤销时效由会话校验窗口约束 |

会话只保存 `userId`、`workspaceId`、`platformAccountId`、`platformAppInstanceId`、`email`、
`displayName`、`roles`、`permissions` 与 `expiresAt`，**不含**任何 Token、Launch Code、
Client Secret 或完整 Claims。

会话形态由 `AdminSessionPrincipal` 在构造时固化，只有三种：平台会话（工作区与完整平台边界
齐全）、本地工作区会话（只有本系统的自有工作区）、自举会话（两者都还没有，用于本地首次
部署与排障）。

会话建立后仍有逐请求复验，由平台集成 Starter 的**会话有效性管线**统一执行：绝对过期 →
工作区状态 → 平台成员有效性。本仓库只提供两个 SPI（`identity.infrastructure.
SessionPipelineAdapters`：会话边界映射、工作区读取）。本地工作区会话没有平台成员，只走前两层，
租户边界由工作区状态承担；自举会话不参与管线，绝对过期由本仓库的 `SessionAbsoluteExpiryFilter`
保证。任一环节不通过即失效会话并返回 `401`。

因此平台暂停或注销实例、本地工作区被停用，最迟在一个校验窗口内生效（写请求 30 秒、读请求
60 秒），而不是等到会话自然过期。

平台映射账号与本地账号在同一个 `admin_user` 表中，用 `credential_source` 区分：

- `LOCAL`：有口令摘要，邮箱唯一（部分唯一索引）。
- `PLATFORM`：无口令，`(platform_app_instance_id, platform_user_id)` 唯一；邮箱允许与本地账号重名，
  避免用邮箱碰撞把平台身份接到本地账号上造成接管。

账号被禁用时同时撤销该主体的全部服务端会话，不等会话自然过期。

## 4. 权限模型

权限码与 admin-design 前端共用一套约定：`*` 全部、`<命名空间>.<资源>.*` 资源内全部、其余精确匹配；
命名空间取本部署在平台登记的模块标识（模板默认 `example.admin`），格式与平台
`BUSINESS_RBAC` 的 `<module-namespace>.<resource>.<action>` 一致。

服务端在建立会话时用 `PermissionCatalog.expand` 把通配展开成具体码写入 Spring Security 的授权集合，
因此 `@PreAuthorize("hasAuthority('example.admin.member.view')")` 对持有 `*` 或
`example.admin.member.*` 的账号同样成立。
接口返回给前端的是**原始**权限码，由前端自行做通配过滤。

权限目录是代码内置的（`PermissionCatalog`），角色与授予关系在数据库（`admin_role`、
`admin_role_permission`、`admin_user_role`），内置角色由 Flyway 迁移写入。

## 5. 租户隔离

`admin_workspace` 是所有业务数据的隔离根，也是本系统**自有的租户容器**，来源分两种：

- `PLATFORM`：平台开通实例时创建，保留 `platform_account_id`、`platform_app_instance_id`、
  `module_key`、`module_version`、`external_instance_id` 等平台边界字段，与平台 `AppInstance`
  一一对应；
- `LOCAL`：本系统自建。不接平台独立运行时由第一个本地账号注册时引导创建，平台字段全部为空，
  同一部署内的本地账号共用一个本地工作区。

平台映射只是工作区的一种来源，不是它的定义。

- 工作区标识只来自已认证会话的 `AdminSessionPrincipal.workspaceId`。
- 请求体、Query 与自定义 Header 中的 Account、实例、工作区声明一律不可信。
- 示例业务域 `notice` 用 `workspace_id` 体现该规则：公告只对所属工作区可见；没有工作区的
  自举会话被拒绝（`WORKSPACE_REQUIRED`），不再存在"`NULL` 即公共数据"的语义。

平台生命周期入参里的 `accountId`、`appInstanceId` 只在**首次建档**时使用，不能作为普通业务请求的租户凭据。

## 6. 平台接入结构

| 关注点 | 位置 | 说明 |
| --- | --- | --- |
| 出站 Launch 交换 | `identity.infrastructure.SdkPlatformLaunchGateway` | 只做凭据注入与失败分类，协议由 SDK 实现 |
| SDK 组件装配 | `platform-integration-spring-boot-starter` | 运行时客户端、模块服务身份、联机探针、关联标识都按 `platform.integration.*` 自动装配 |
| 会话有效性管线 | 同上，本仓库只提供两个 SPI | 绝对过期 → 工作区状态 → 成员有效性；本地工作区会话跳过成员层 |
| 入站生命周期鉴权 | `access.infrastructure.security.LifecycleSecurityConfiguration` | 独立安全链，按 Scope 逐端点授权 |
| 生命周期幂等 | `access.infrastructure.JdbcWorkspaceLifecycle` | 幂等键 + 请求摘要，冲突返回 409 |

对应关系：`runtime_*` 面向用户上下文 Token，由 SDK 验签；`service_*` / `lifecycle-audience`
面向平台调用的生命周期服务 Token，由独立资源服务器链校验 Issuer、Audience 与 Scope。
两套 Issuer/JWKS 不能混用。

`admin.platform.enabled=false` 时：Launch 入口由 `DisabledPlatformLaunchGateway` 失败关闭，
生命周期入口由 `LifecycleDisabledSecurityConfiguration` 整体拒绝。

## 7. 事务、幂等与可观测性

- 生命周期命令先在本地事务内持久化操作状态再返回结果，进程重启后仍可查询。
- 相同幂等键 + 相同请求摘要返回首次结果；相同幂等键 + 不同摘要返回 `IDEMPOTENCY_CONFLICT`。
- 审计使用独立事务追加，业务回滚时登录失败等安全事实仍然留痕。
- 关联标识由服务端生成或采纳通过严格格式校验的入站 `X-Request-Id`，只用于串联日志，
  绝不作为身份、租户或授权依据。
- 所有失败统一为 `application/problem+json` 并带稳定 `code`。

## 8. 明确不做

- 不缓存或转发平台用户 Token 给浏览器。
- 不把平台 `Account`/`AppInstance` 当作本地表的租户主键，只保存映射。
- 不在本仓库实现资源配额、AI、Capability、Event、统一通知与组织目录；
  出现真实用例时按接入标准逐项启用。
