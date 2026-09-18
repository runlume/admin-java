# 平台接入契约

本文是**本仓库自己维护**的接入契约，只覆盖 admin-java 实际实现的**最小接入**：统一开通与登录。
平台侧的完整接入标准由平台侧维护；这里不复述，也不复制平台内部实现。

## 1. 分工

| 事项 | 平台 | 本系统 |
| --- | --- | --- |
| 用户、Account、实例成员、Launch Grant | 负责 | — |
| 模块登记、版本发布、实例开通 | 负责 | 提交契约包并保持实现兼容 |
| 验证 Token、映射本地用户、建立本地会话 | — | 负责 |
| 生命周期命令的幂等执行 | 调用方 | 负责 |
| 业务数据与租户隔离 | — | 负责 |

边界：平台不跨库查询本系统，本系统也不把平台用户 Token 缓存或转发给浏览器。

## 2. 最小接入范围

本系统需要提供四件事：

1. **生命周期端点**：开通、暂停、恢复、注销四个命令，加一个异步操作查询；
2. **Launch 入口**：接收一次性 Code、与平台交换、验证结果并建立本地会话；
3. **联机探针**：登录说明页用的同源状态接口；
4. **工作区映射**：平台实例对应本系统的一个 `PLATFORM` 工作区，且业务表都带 workspace 维度
   （不接平台独立运行时，工作区也可以由本系统自建，见 [architecture.md](architecture.md) 第 5 节）。

需要向平台登记的固定值有四个，都由部署配置提供，不从请求读取：

| 值 | 作用 | 本仓库配置 |
| --- | --- | --- |
| 模块标识 | 平台识别本系统 | `admin.platform.module-id` |
| 运行时 Audience | 平台签给本系统的用户上下文 Token 的 `aud` | `admin.platform.runtime-audience` |
| 生命周期 Audience | 平台调用本系统生命周期端点的 `aud` | `admin.platform.lifecycle-audience` |
| 生命周期入口 | 平台可达的 `baseUrl` 与本文第 4 节路径 | 由平台登记，不在本仓库 |

模块清单文件与登记流程由平台侧负责，本仓库不包含 Manifest。

## 3. Launch

平台签发的是一次性 Launch Grant，浏览器拿到的只有短期 Code，用户不会把平台访问令牌带进本系统。
链路是：平台签发 Launch Grant 与 Code → 浏览器以表单把 Code 提交到本系统的
`admin.platform.launch-path` → 本系统用模块服务身份与平台交换 → 平台返回用户上下文 Token →
本系统验证并建立本地会话 → 返回 `303` 跳转到站内目标路径。

本系统必须做到：

- Code 只出现在 `Cache-Control: no-store` 响应与表单 Body 中，不写入 URL、Referer、日志或分析系统；
- 验证用户上下文 Token 的 `iss`、`aud`、`kid`、签名与 `exp`；未知 `kid` 只触发一次受限的公钥刷新，
  不允许跳过验签；
- 本地会话的绝对过期时间取 `admin.local.session-ttl`，**不再压缩到 Token 的 `exp`**：短票据只
  证明进入时点，之后由会话校验按声明的窗口（写 30 秒 / 读 60 秒）持续确认成员仍然有效，
  撤销时效由窗口而非会话时长决定；本地仍不提供刷新令牌或滑动续期；
- Code 只由目标模块交换一次。本仓库把交换交给 `platform-integration-sdk-java` 完成，
  适配器只负责注入模块服务凭据并把失败收敛成稳定错误码；
- 建立会话前确认本地工作区处于 ACTIVE，暂停与注销状态拒绝新会话。

## 4. 生命周期

方向是**平台 → 本系统**。每个写命令必须带 `Idempotency-Key`，每个端点只接受对应的最小 Scope。

| 方法 | 路径 | Scope | 成功响应 |
| --- | --- | --- | --- |
| `POST` | `/integration/v1/app-instances` | `instance:provision` | `201`，返回不透明实例标识 |
| `GET` | `/integration/v1/operations/{operationId}` | `instance:operation:read` | `200` 操作结果 |
| `POST` | `/integration/v1/app-instances/{externalInstanceId}/suspend` | `instance:suspend` | `200/202` |
| `POST` | `/integration/v1/app-instances/{externalInstanceId}/resume` | `instance:resume` | `200/202` |
| `DELETE` | `/integration/v1/app-instances/{externalInstanceId}` | `instance:deprovision` | `204/202` |

幂等与事务要求：

- 相同幂等键 + 相同请求摘要返回第一次结果；相同幂等键 + 不同摘要返回 `409`；
- 先持久化命令与状态再执行，进程重启后仍能查询同一 `operationId`；
- 外部调用不持有数据库事务或行锁；
- `SUSPENDED` 与 `DEPROVISIONING` 必须阻止新会话与**每一次**业务写，而不只是隐藏入口；
- `DEPROVISIONED` 立即撤销访问。

`externalInstanceId` 是本系统返回给平台的不透明标识，平台不解析其结构；请求体里的 Account 与实例标识
只在首次建档时使用，不能作为普通业务请求的租户凭据。

## 5. 两类 Token

两套令牌的 Issuer、Audience 和用途都不同，不能混用：

| | 用户上下文 Token | 生命周期服务 Token |
| --- | --- | --- |
| 谁签发 | 平台运行面 | 受控 OIDC Provider |
| 谁持有 | 本系统（只在服务端） | 平台 |
| `aud` | 本系统运行时 Audience | 本系统生命周期 Audience |
| 用途 | Launch 后建立本地会话 | 平台调用本系统生命周期端点 |
| 本仓库校验方式 | `PlatformJwtVerifier`（SDK 提供） | 独立资源服务器安全链 |

生命周期入口按端点校验 Scope，Scope 不足返回 `403`；鉴权失败与参数错误都返回稳定的
`application/problem+json`，不返回堆栈或上游响应体。

## 6. 联机探针

`GET /api/v1/platform-connection` 供登录说明页使用：匿名、固定 `Cache-Control: no-store`、
响应体**只有** `connected` 布尔值。目标地址只取 `admin.platform.runtime-jwks-uri` 配置，
浏览器参数不能覆盖；连接与请求超时各 5 秒、禁止重定向、响应上限 64 KiB，
并且只在 HTTP 200、JSON 合法且 `keys` 为非空数组时才为 `true`。

它证明"后端能解析平台公钥"，不证明浏览器已登录、用户已获实例授权或 Launch 链路可用。
实现见 `PlatformConnectionProbe`。

## 7. 启用清单

1. 由平台侧登记模块、发布版本，并生成模块级 Client 凭据；
2. 在运行环境注入 `ADMIN_PLATFORM_ENABLED=true` 与第 2 节的四个固定值；
3. 通过环境变量或密钥管理注入 `service-client-id` 与 `service-client-secret`，不要写进仓库；
4. 确认平台可达本系统的生命周期 `baseUrl`，且生命周期入口使用 HTTPS；
5. 验证：未登录访问 `/api/v1/me` 返回 `401`；同一 `Idempotency-Key` 重复开通只产生一个工作区；
   从平台发起真实 Launch 后 `GET /api/v1/me` 返回 `200`。

## 8. 不在最小接入范围

资源与用量上报、平台 AI、跨系统能力调用、事件订阅与投递、统一通知、组织目录、文件服务，
以及面向私有化交付的 Deployment License，都不属于最小接入。

出现真实用例时按平台侧的完整接入标准逐项启用，**不要**在模板里预留空实现或空接口。
