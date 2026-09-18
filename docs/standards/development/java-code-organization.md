# Java 代码组织规范

## 1. 适用范围

本文约束 Java 包、模块、类型职责、持久化所有权以及事务与副作用的组织方式。它不固定根包、业务模块、框架、数据库或交付物；采用项目必须在自己的开发 Profile 中声明这些值。

编码风格、设计原则、设计模式、Java 25 和算法由第 6 节链接的独立规范负责。

## 2. 包与模块

Java 根包使用组织反向域名和项目标识，例如：

```text
com.example.product
```

直接子包优先表达业务能力或技术上独立的交付职责，不按 Controller/Service/Repository 建全局横向大包。项目 Profile 应列出允许的顶级模块、组合根和共享包边界。

模块内部按真实复杂度选择：

```text
<module>/
├── api/              # 跨模块公开命令、查询结果、服务和事件
├── application/      # 用例、授权、事务和流程编排
├── domain/           # 状态、不变量、值对象和策略
└── infrastructure/   # Web、数据库、消息、Worker 和框架装配
```

- 简单模块可以省略不需要的层，禁止机械创建空包、空接口和一层转发。
- 模块根包或项目明确标记的 `api` 是跨模块公开面；包名本身不自动获得公开资格。
- 其他模块不得依赖 `application`、`domain`、`infrastructure` 或未公开类型。
- `shared`/`common` 只允许无领域所有权、稳定且无状态的类型，不能成为公共 DTO、Service 或工具堆放区。
- 包依赖必须无循环；循环表示所有权或公开面需要重新设计。

## 3. 类型职责

| 类型 | 负责 | 禁止 |
|---|---|---|
| Controller/Handler | 协议映射、反序列化、输入校验、可信身份转换、状态码/错误映射 | 业务决策、事务编排、直接数据访问、信任请求中的权限或归属字段 |
| Application Service | 单个或高度内聚用例、授权、事务边界、领域和 Port 协调 | 拼装具体协议响应、包含供应商 DTO、跨模块直接写数据 |
| Domain/Policy | 业务不变量、状态转换、值对象和确定性策略 | Web/数据库框架、网络、Secret、当前请求对象 |
| Infrastructure Adapter | 数据访问、外部协议、消息、Secret、Worker 和框架装配 | 决定产品规则、向公开 API 泄漏基础设施类型 |
| Mapper/Factory | 无 I/O 的显式转换或受控创建 | 查询数据库、读取安全上下文、隐藏事务和授权 |
| Utility | 多处复用、输入输出明确的纯函数 | I/O、时钟、配置、事务、权限和领域流程 |

出现以下信号时按变化原因拆分类：

- 同一个类因互不相关需求反复修改；
- 同时处理协议、领域判断与持久化；
- 私有方法形成多组无关流程；
- 构造器依赖来自多个无关模块；
- 测试一个规则要替换大量无关协作者。

不按行数或方法数量机械拆分。禁止 `BaseController`、`BaseService`、万能 CRUD、Service Locator 和 God Service。真实存在第二实现或需要隔离外部依赖时才抽象接口。

如果采用 Spring MVC 与 Contract First，每个映射方法的 Javadoc、Controller 和 OpenAPI 分工按[OpenAPI 与 Controller 开发规范](openapi-and-controller-guidelines.md)执行。

## 4. 持久化组织

- Schema/Migration 的唯一真源、主业务数据访问方式和例外流程由项目 Profile 明确。
- 一个表、集合或索引只有一个 owning module；其他模块通过拥有方公开 API 访问。
- 持久化生成类型、查询对象、连接和事务只存在于拥有模块的 infrastructure，不进入 Domain 或跨模块 API。
- 不在同一业务域无理由并行维护 ORM、SQL DSL、模板 JDBC 等多套持久化风格。
- 主数据访问方式无法表达需求时，精确记录例外类型/语句、原因、Owner、参数化、隔离测试和移除条件。
- 允许语义明确的 `XxxStore`、`XxxQuery` 或 `XxxRepository`；禁止泛型 Repository 和没有隔离价值的一层接口。
- Persistence Port 定义在拥有模块的 application（确属领域抽象时可在 domain），Adapter 放在 infrastructure；内层不能反向依赖 Adapter。
- 数据归属、唯一性、复合关系和状态条件不能只靠 Controller 过滤，必须由数据库/存储约束或条件更新保护。

具体 Migration、Codegen 和数据库发布规则见[数据库版本与 Migration 开发规范](database-version-and-migration-guidelines.md)。

## 5. 事务与外部副作用

- Application Service 定义本地事务边界；Domain 不依赖事务框架。
- 一个本地事务可以调用多个公开模块服务维护强不变量，但每份数据仍只有一个拥有模块。
- 本地事务内不调用远程 HTTP、消息供应商、支付、邮件、对象存储或其他网络服务。
- 需要崩溃恢复的副作用先写持久化 Operation、Outbox 或 Delivery，提交后执行，结果在新短事务登记。
- 不承诺 Exactly Once；外部命令和消费者按稳定业务键幂等。
- 进程内事件只表达同进程通知，不能冒充可靠外部队列。

## 6. 关联规范

- 命名、格式、花括号、Lambda、错误、日志、空值和测试见[Java 代码质量规范](java-code-quality-guidelines.md)。
- SOLID、内聚、耦合、KISS、DRY 和 YAGNI 见[软件设计原则](software-design-principles.md)。
- GoF 23 种及企业应用模式见[设计模式规范](design-pattern-guidelines.md)。
- 分层、六边形、清洁、模块化单体、事件驱动和微服务选择见[架构模式选择规范](architecture-pattern-selection.md)。
- 集合、复杂度、查询、排序、搜索和批处理见[数据结构与算法规范](data-structures-and-algorithms.md)。
- Java 25 语言与 JVM 能力见[Java 25 语言与运行时规范](java-25-language-and-runtime-guidelines.md)。

## 7. 组织评审清单

- [ ] 项目 Profile 已声明根包、模块、公开面、组合根和持久化方式。
- [ ] Controller、Application、Domain、Infrastructure 职责没有混合。
- [ ] 跨模块只依赖公开 API，不泄漏持久化或供应商类型。
- [ ] 一个数据对象只有一个拥有模块，没有无理由的持久化双栈。
- [ ] 外部调用不在数据库事务内，可靠副作用已持久化。
- [ ] 身份和资源归属从可信上下文取得，并在持久化层交叉校验。
- [ ] 集合、批次、重试、并发和响应大小都有上限。
- [ ] 代码、测试、项目 Profile 和相关契约在同一提交保持一致。
