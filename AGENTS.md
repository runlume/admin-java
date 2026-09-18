# Admin Java 协作约定

本目录是「Runlume 标准后台后端」模板，供业务系统复制使用。开始改动前先读 `README.md`、
[docs/project-development-profile.md](docs/project-development-profile.md) 与 `docs/project/`，
通用规范在 `docs/standards/development/`。

## 范围

- 这里只放**后端标准**：本地身份与会话、角色权限、平台最小接入、租户隔离、
  可观测性与错误契约。
- 不放平台控制面能力（Account 定价、订阅、Capability 网关、统一通知中心）。
  业务系统是仓库外的独立系统，不能跨库查询平台或其它 SaaS。
- 业务域示例（`notice`）只用于演示边界，不能沉淀成通用业务模型。
- 不引入未声明消费者的抽象：出现真实用例再增加端口与适配器。

## 执行约束

- Java 25 + Gradle Wrapper（本仓库会拒绝其它 Java 版本），只改 `build.gradle` 与
  `gradle/libs.versions.toml` 管理依赖版本。
- 数据库结构只由 `src/main/resources/db/migration` 下的 Flyway Migration 建立；
  禁止手工建表或从开发者已有数据库生成 jOOQ 类型。
- 生产源码顶层类型的类级 Javadoc 必须写 `@author 树深技术` 与 `@since 0.1 at yyyy/M/d HH:mm`。
- 业务域只能通过 `access.identity` 与 `access.observability` 两个 Named Interface 访问
  `access`；禁止直接依赖 `access.*.infrastructure`、平台 SDK 与 jOOQ 生成类型。
- 租户、Account 与工作区标识只能来自已认证会话或平台生命周期首次建档，禁止从请求体、
  Query 或自定义 Header 读取。
- 会话、Token、Launch Code、Client Secret、口令与完整载荷不得写入日志、审计或响应。
- 新增 Controller 或错误码时同步 `docs/project/api.md`。
- 平台接入的接口契约看 `docs/project/platform-integration.md`；平台专有的完整接入标准
  不在本仓库，需要时到平台仓库查阅，禁止把平台内部实现与规划复制进来。

## 验证

- 每次改动至少运行 `./gradlew build`（编译 + Checkstyle + 全部测试）。
  Testcontainers 需要容器运行时，macOS + Podman 先设置：

  ```bash
  export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
  export TESTCONTAINERS_RYUK_DISABLED=true
  ```

- 改表必须同时改 Migration、jOOQ 生成清单与使用它的代码，`generateJooq` 会校验表集合。
- 新能力要满足三件套：**实现 + 契约文档 + 覆盖用例**。
- 说明实际结果与未覆盖边界；不得通过删除或跳过用例让校验变绿。
