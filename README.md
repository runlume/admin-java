<a href="https://runlume.app">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/brand/wordmark-dark.svg" />
  <img src="docs/brand/wordmark-light.svg" alt="Runlume" width="220" />
</picture>
</a>

# 标准后台后端

[![开源协议](https://img.shields.io/github/license/runlume/admin-java?style=flat-square&label=%E5%BC%80%E6%BA%90%E5%8D%8F%E8%AE%AE&color=97ca00)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-1f6feb?style=flat-square)](docs/standards/development/java-25-language-and-runtime-guidelines.md)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6db33f?style=flat-square)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169e1?style=flat-square)](https://www.postgresql.org/)

接口契约：[docs/project/api.md](docs/project/api.md) · 文档：[docs/README.md](docs/README.md) · [English](README.en.md)

与 [标准后台前端](https://github.com/runlume/admin-design) 配套的后端标准起点：默认接入
`platform-integration-sdk-java` 的**最小接入**路径，自带**本地用户管理与登录**，业务系统只需要接自己的菜单与业务域。
界面语言与视觉规范用 admin-design，接口契约、会话、权限、租户隔离与平台接入用这里的一份。

Java 25 + Spring Boot 4.1 + Spring Security + PostgreSQL 18 + Flyway + jOOQ + Spring Modulith，
构建工具只用 Gradle Wrapper，密码哈希用 BCrypt。

## 能力范围

| 能力 | 说明 |
| --- | --- |
| 本地账号 | 注册、登录、退出、当前用户、角色与权限码，BCrypt 口令，账号禁用即时撤销会话 |
| 服务端会话 | Spring Session JDBC + 不透明 Cookie，绝对过期时间，登录与 Launch 共用同一条建立路径 |
| CSRF | Cookie + 请求头双提交，`GET /api/v1/csrf` 取令牌 |
| 账号与角色管理 | 分页查询、新建、改名、改角色、启用/禁用（`user:*`、`role:*` 权限码） |
| 平台最小接入 | Launch 换码建会话、生命周期四命令（幂等）、平台联机探针、workspace 映射 |
| 平台边界 | 生命周期入口按服务身份与 Scope 逐端点授权，运行时 Token 由 SDK 验签 |
| 租户隔离 | 所有业务表带 workspace 维度，工作区标识只来自已认证会话 |
| 可观测性 | 服务端关联标识、非敏感审计、`application/problem+json` 稳定错误码 |
| 示例业务域 | `notice`（公告）演示业务域与 `access` 模块的标准边界 |

不包含：资源配额、平台 AI、Capability、Event、统一通知、组织目录和私有化 Deployment License
（属于**完整接入**，见 [分阶段接入](#分阶段接入)）。

## 快速开始

前置：Java 25、PostgreSQL 18、可用的容器运行时（构建期 jOOQ 代码生成与集成测试需要）。

```bash
# 1. 启动本地 PostgreSQL（示例）
podman run -d --name admin-db -p 5432:5432 \
  -e POSTGRES_DB=admin -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin \
  postgres:18.4

# 2. 启动应用：Flyway 自动建表，首次访问首页即为空白后台
ADMIN_DB_PASSWORD=admin ADMIN_SESSION_COOKIE_SECURE=false \
  ./gradlew bootRun

# 3. 注册第一个账号：系统内还没有账号时，首位注册者获得 admin 角色
curl -c jar -X GET http://localhost:8080/api/v1/csrf
curl -b jar -c jar -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(curl -b jar -s http://localhost:8080/api/v1/csrf | sed 's/.*"token":"\([^"]*\)".*/\1/')" \
  -d '{"email":"admin@runlume.local","displayName":"系统管理员","password":"runlume-password"}'
```

`ADMIN_SESSION_COOKIE_SECURE=false` 只用于明文 HTTP 联调；生产必须保持默认的 `true`。

## 构建与校验

```bash
./gradlew build        # 编译 + Checkstyle + 全部测试
./gradlew test         # 单元与集成测试（Testcontainers 起临时 PostgreSQL 18）
./gradlew check        # 完整门禁
./gradlew generateJooq # 只从 Flyway Migration 重新生成 jOOQ 类型
./gradlew bootRun      # 本地启动
```

集成测试与 jOOQ 代码生成需要容器运行时。macOS + Podman 的做法与平台仓库一致：

```bash
export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
export TESTCONTAINERS_RYUK_DISABLED=true
./gradlew check
```

数据库结构**只**由 `src/main/resources/db/migration` 下的 Flyway Migration 建立；
jOOQ 类型每次生成都会新建临时 PostgreSQL 18 并完整迁移，不允许从开发者已有数据库生成。
改表必须同时改 Migration 与使用它的代码，`./gradlew build` 会重新生成并校验表清单。

## 依赖来源

`platform-integration-sdk-java` 由 Runlume Nexus 提供，已在 `settings.gradle` 中声明为
`https://nexus.runlume.app/repository/maven-public/`（只对 `app.runlume.*` 生效）。
需要鉴权时通过 Gradle 属性提供，不要把凭据写进仓库：

```bash
./gradlew build -PrunlumeNexusUsername=... -PrunlumeNexusPassword=...
```

## 接口一览

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/v1/csrf` | 匿名 | 取 CSRF 令牌与请求头名称 |
| `POST /api/v1/auth/register` | 匿名 | 注册并建立会话 |
| `POST /api/v1/auth/login` | 匿名 | 邮箱口令登录 |
| `POST /api/v1/auth/logout` | 会话 | 退出并失效服务端会话 |
| `GET /api/v1/me` | 会话 | 当前用户、角色与权限码 |
| `GET /api/v1/permissions` | 会话 | 内置权限目录 |
| `GET /api/v1/users` | `user:view` | 分页查询账号 |
| `POST /api/v1/users` | `user:create` | 新建账号 |
| `GET /api/v1/users/{id}` | `user:view` | 账号详情 |
| `PATCH /api/v1/users/{id}` | `user:update` | 改名与角色 |
| `POST /api/v1/users/{id}/status` | `user:disable` | 启用/禁用并撤销会话 |
| `GET /api/v1/roles` | `role:view` | 角色与权限 |
| `GET /api/v1/platform-connection` | 匿名 | 平台联机状态，只返回布尔值 |
| `POST /launch` | 匿名（契约豁免 CSRF） | 平台 Launch 换码并建立会话 |
| `POST /integration/v1/app-instances` | `instance:provision` | 开通实例 |
| `GET /integration/v1/operations/{id}` | `instance:operation:read` | 查询操作 |
| `POST /integration/v1/app-instances/{id}/suspend` | `instance:suspend` | 暂停实例 |
| `POST /integration/v1/app-instances/{id}/resume` | `instance:resume` | 恢复实例 |
| `DELETE /integration/v1/app-instances/{id}` | `instance:deprovision` | 注销实例 |
| `GET POST PATCH /api/v1/notices` | `notice:view` / `notice:manage` | 示例业务域：公告 |

登录、Launch 与错误响应都返回稳定的错误码（`EMAIL_ALREADY_REGISTERED`、`INVALID_CREDENTIALS`、
`IDEMPOTENCY_CONFLICT` 等），响应体为 `application/problem+json` 且带 `code` 字段，便于前端映射文案。

## 关键约定

- **权限码**：`*` 全部、`模块:*` 模块内全部、其余精确匹配，与 admin-design 前端一致。
  服务端把通配展开成具体权限码后再授权，避免"前端可见、后端拒绝"。
- **会话只存派生身份**：`AdminSessionPrincipal` 不含平台 Token、Launch Code、Client Secret 或完整 Claims；
  本地会话绝对过期不得晚于平台 Context Token 的 `exp`。
- **租户只来自会话**：请求体、Query 与自定义 Header 中的 Account、实例与工作区声明一律不可信。
- **平台入口失败关闭**：`admin.platform.enabled=false` 时 Launch 与生命周期入口整体拒绝，后台仍可独立运行。
- **审计只追加终态事实**：Token、Secret、口令、完整载荷和原始请求不进审计表。
- **不新增无消费者的抽象**：接入能力按真实用例逐个启用，不做"以后可能用到"的预留。

## 目录结构

```text
src/main/java/app/runlume/admin/
├── AdminJavaApplication.java
├── access/                       平台集成 owning module
│   ├── WorkspaceView.java        平台实例与本地工作区映射
│   ├── WorkspaceDirectory.java   只读入口
│   ├── WorkspaceLifecycle.java   生命周期命令的幂等入口
│   ├── PlatformIntegrationProperties.java
│   ├── identity/                 Named Interface "identity"
│   │   ├── AdminSessionPrincipal.java
│   │   ├── AdminIdentity.java
│   │   ├── PermissionCatalog.java
│   │   └── infrastructure/
│   │       ├── SdkPlatformLaunchGateway.java    platform-integration-sdk-java 适配器
│   │       ├── ModuleServiceTokenProvider.java
│   │       ├── PlatformConnectionProbe.java
│   │       ├── security/                        安全链、会话建立、会话撤销
│   │       └── web/                             会话与账号管理 API
│   ├── observability/            Named Interface "observability"
│   └── infrastructure/           jOOQ 实现、生命周期入站、Problem 处理
└── notice/                       示例业务域：只通过 Named Interface 访问 access

src/main/resources/db/migration/  Flyway 基线
src/jooqCodegen/                  jOOQ 代码生成启动器（只在构建类路径）
docs/                             本项目文档与内置标准
```

架构与边界详见 [docs/project/architecture.md](docs/project/architecture.md)，
接口与错误码详见 [docs/project/api.md](docs/project/api.md)。

## 作为模板使用

1. **复制整个目录**，改 `settings.gradle` 的 `rootProject.name`、`build.gradle` 的 `group`/`description`
   与 `application.yml` 的 `spring.application.name`。
2. **换包名**：把 `app.runlume.admin` 改成自己的根包，并同步 `JooqCodegen` 中的生成包名与表清单。
3. **换业务域**：删除或改写 `notice` 包，新增与你业务平级的包；只通过
   `access.identity` 与 `access.observability` 两个 Named Interface 访问平台集成事实。
4. **换数据库**：改 `ADMIN_DB_*` 与 `src/main/resources/db/migration/V001__baseline.sql`，
   业务表必须带 workspace 维度。改完执行 `./gradlew generateJooq`。
5. **换权限**：改 `PermissionCatalog` 与 `V002__built_in_roles.sql`，权限码格式保持
   `模块:动作`，前端 admin-design 无需改动即可用通配匹配。
6. **接平台**：从 [平台接入配置](#平台接入配置) 填入模块标识、Issuer、JWKS 与 Audience，
   把 `admin.platform.enabled` 打开，并在平台侧完成模块登记与 Client 凭据配置。
7. **换品牌**：`banner.txt`、README 与 `docs/`。

## 平台接入配置

默认关闭。打开后所有平台地址只取配置，浏览器与请求参数不能覆盖；Client Secret 只来自环境变量或密钥管理。

| 配置 | 环境变量 | 说明 |
| --- | --- | --- |
| `admin.platform.enabled` | `ADMIN_PLATFORM_ENABLED` | 是否启用平台接入 |
| `admin.platform.module-id` | `ADMIN_PLATFORM_MODULE_ID` | 平台登记的稳定模块标识 |
| `admin.platform.base-uri` | `ADMIN_PLATFORM_BASE_URI` | 平台控制面地址 |
| `admin.platform.runtime-issuer` / `runtime-jwks-uri` | `ADMIN_PLATFORM_RUNTIME_*` | 平台运行时 Issuer 与公钥地址 |
| `admin.platform.runtime-audience` | `ADMIN_PLATFORM_RUNTIME_AUDIENCE` | 平台签给本系统的运行时 Audience |
| `admin.platform.launch-path` | `ADMIN_PLATFORM_LAUNCH_PATH` | 浏览器提交 Launch Code 的路径 |
| `admin.platform.service-issuer` / `service-jwks-uri` | `ADMIN_PLATFORM_SERVICE_*` | 平台服务身份 Issuer 与公钥地址 |
| `admin.platform.service-audience` | `ADMIN_PLATFORM_SERVICE_AUDIENCE` | 模块服务 Token 的 Audience |
| `admin.platform.lifecycle-audience` | `ADMIN_PLATFORM_LIFECYCLE_AUDIENCE` | 平台调用生命周期端点的 Audience |
| `admin.platform.service-token-uri` / `service-client-id` / `service-client-secret` | `ADMIN_PLATFORM_SERVICE_*` | 模块服务身份令牌地址与凭据 |
| `admin.local.registration-enabled` | `ADMIN_REGISTRATION_ENABLED` | 是否开放自助注册 |
| `admin.local.session-ttl` | `ADMIN_SESSION_TTL` | 本地会话绝对有效期 |

### 分阶段接入

当前是**最小接入**：统一开通与登录。出现资源、AI、Capability 或跨 SaaS 数据交换需求时，再按
[平台接入契约](docs/project/platform-integration.md) 核对边界，并按平台仓库的完整接入标准逐项启用，
不要提前做空实现。

`Spring Boot` 的 Deployment License 自动配置在本模板中被显式排除：
REMOTE 接入不要求业务 SaaS 持有或校验 Deployment License。交付客户可自行运行的私有化镜像时，
删除 `application.yml` 中的 `spring.autoconfigure.exclude` 项并按 License 运行门禁文档补齐配置。

## 文档

- [docs/README.md](docs/README.md)：文档索引与本项目固定值
- [docs/project/architecture.md](docs/project/architecture.md)：模块边界、会话与租户模型
- [docs/project/api.md](docs/project/api.md)：接口契约、错误码与状态语义
- [docs/project/template.md](docs/project/template.md)：作为模板复制的检查清单
- [docs/project/platform-integration.md](docs/project/platform-integration.md)：本仓库实现的最小接入契约
- [docs/standards/](docs/standards/)：内置的通用开发规范；平台专有接入标准不随本仓库公开（含原因说明）

## 开源协议

[MIT](LICENSE)，Copyright (c) 2026 Runlume。可以自由复制进商业产品，只需保留版权与许可声明；
`docs/brand/` 下的品牌标识属于品牌资产，使用时替换成自己的。细节见 [docs/guide/license.md](docs/guide/license.md)。
