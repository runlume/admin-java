# 项目开发约束 Profile

> 文档状态：已生效，项目专属
> 通用规范：[通用开发规范索引](standards/development/README.md)
> 接入契约：[平台接入契约](project/platform-integration.md)

本文把通用开发规范绑定到 admin-java 的具体值。通用原则不在这里复制；
换项目时替换本文件，而不是修改 `docs/standards/development/`。
发生冲突时，安全、租户隔离与已发布契约优先，其次是本文与通用规范。

## 1. 技术与交付物

- 构建：Gradle Wrapper 9.7、Groovy DSL。
- 运行时：Java 25、Spring Boot 4.1.x、Spring Modulith 2.1.x、Spring MVC、Spring Security。
- 数据：PostgreSQL 18.x、Flyway Versioned SQL、jOOQ/JDBC。
- 会话：Spring Session JDBC 与服务端不透明 Cookie，浏览器不保存访问令牌。
- 平台接入：`platform-integration-sdk-java`，从 Nexus 的 `maven-public` 取得。
- 本仓库只有一个可执行制品 `admin-java`；不拆分微服务、独立 Worker 或公共库。

未出现量化证据前，不引入 Redis、Kafka、服务网格或通用插件平台。

## 2. Java 包与模块

- 根包固定为 `app.runlume.admin`。
- 顶级 Spring Modulith 模块只有两个：`access`（平台集成 owning module）与 `notice`（示例业务域）。
- 业务域可见的 Named Interface 只有 `access.identity` 与 `access.observability`；
  `access.*.infrastructure` 不对外开放。
- 启动类只有一个 `AdminJavaApplication`。
- Domain 与 API 不依赖 Spring MVC、jOOQ 或供应商 SDK；生产代码不引入 JPA/Hibernate、
  WebFlux Server 或 R2DBC。
- 平台 SDK 只允许出现在 `access` 的 `infrastructure` 适配器内，不得泄漏到业务域。

## 3. 安全与租户边界

- 工作区标识只来自已认证会话的 `AdminSessionPrincipal`；请求体、Query 与自定义 Header
  中的 Account、实例、工作区声明一律不可信。
- 平台生命周期入参里的 Account 与实例标识只在首次建档时使用。
- 会话、Token、Launch Code、Client Secret、口令与完整业务载荷不写日志、审计与响应。
- 账号禁用必须同时撤销该账号的全部服务端会话。
- 生命周期入口逐端点校验 Scope；鉴权失败与参数错误统一返回稳定
  `application/problem+json`，不返回堆栈或上游响应体。

## 4. 数据库 Profile

- 迁移目录固定为 `src/main/resources/db/migration/`，命名为 `VNNN__owner_bounded_purpose.sql`。
- 当前基线为 `V001__baseline.sql`（Schema）与 `V002__built_in_roles.sql`（角色与权限码种子），
  新变更从 `V003` 起分配；2026-09-18 执行过一次授权的基线收缩，映射与等价验证见
  [project/migration-baseline.md](project/migration-baseline.md)。
- PostgreSQL 固定 18.x；涉及 PostgreSQL 行为的测试使用真实 PostgreSQL，不以 H2 替代。
- Flyway Versioned SQL 是 Schema 唯一真源；已共享的 Migration 不修改、删除、重命名、复用或 `repair`，
  错误用更高版本前向修复。
- 新表与新列必须在同一 Migration 提供中文 Catalog 注释、约束和必要索引。
- jOOQ 是业务持久化默认方式；JDBC 只保留给 Driver、连接池、Flyway、Spring Session、
  jOOQ 底层与测试探测。
- Codegen 从临时 PostgreSQL 18 空库执行完整 Flyway Schema 后生成到
  `build/generated/sources/jooq/main`；生成源码不提交 Git，Codegen/Meta/Testcontainers
  不进入生产类路径。
- 一张表只有一个 owning module，生成的表类型只允许该 owning module 的 `infrastructure` 使用。
- 业务表必须带 workspace 维度，并让所有查询与唯一约束覆盖它。

## 5. API 与 Controller Profile

- 本仓库暂未引入 `contracts/openapi/*.openapi.yaml` 与生成客户端；HTTP 契约真源是
  [`docs/project/api.md`](project/api.md)，由人维护并与实现同步。
  若后续要求契约优先（OpenAPI → 生成客户端 → Controller → 契约测试），需补 `contracts/`
  目录、生成任务与契约一致性门禁，不能只加 YAML 不进门禁。
- 禁止 Swagger/Springdoc 描述注解和运行时反向生成第二份契约。
- 每个 Spring MVC 映射方法必须有中文 Javadoc；可信主体、工作区等内部身份不得变成客户端输入。
- 请求与响应使用 `application/json`，失败使用 `application/problem+json` 并带稳定 `code`。
- 新增或修改接口时同步 `docs/project/api.md` 与 `docs/project/platform-integration.md`。

## 6. Javadoc Profile

- 生产源码每个顶层类型都必须使用中文传统 `/** */` Javadoc。
- 类级元数据固定为 `@author 树深技术` 与 `@since 0.1 at yyyy/M/d HH:mm`。
- 时间取类型首次加入项目时的 Asia/Shanghai 当前时间；月、日不补零，小时、分钟补足两位，
  后续修改不得刷新。
- 公开契约、跨模块 API、状态机、安全关键逻辑与所有映射方法必须说明非显然的职责、信任、
  幂等、事务、并发与失败边界。
- 方法、构造器、字段与枚举值不添加 `@author`。

## 7. 自动化门禁

```bash
export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
export TESTCONTAINERS_RYUK_DISABLED=true
./gradlew build
```

Windows 与 Linux 的容器运行时设置见 [README 构建与校验](../README.md#构建与校验)。

`build` 必须聚合编译、Checkstyle、测试与 `generateJooq`。jOOQ 生成会断言实际表集合与
`JooqCodegen` 中的期望集合完全一致，新增或删除表却未同步时直接失败；扫描目标为空同样失败。
环境缺失导致的跳过不算通过。

## 8. 项目级例外

偏离必须精确到文件、类型或配置项，并记录原因与移除条件，禁止放宽规则或删除测试让构建变绿。

| 例外 | 原因 | 移除条件 |
| --- | --- | --- |
| `application.yml` 排除 Deployment License 自动配置 | 该自动配置要求业务系统提供启动解析器与上下文，缺失时直接启动失败；REMOTE 接入按契约不需要 Deployment License | 交付客户可自行运行的私有化镜像并按 License 要求提供配置时删除该排除项 |
| 显式声明 Spring Session 的 `CookieSerializer` | Spring Session 先注册 `CookieHttpSessionIdResolver`，使 Boot 会话自动配置不再构建 `CookieSerializer`，`server.servlet.session.cookie.*` 失效 | Boot 或 Spring Session 修正该条件顺序、且回归验证表明配置生效后移除 |
