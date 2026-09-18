# 开发规范索引

> 文档状态：已生效，强制执行  

本目录提供可复用的工程文档治理与前后端开发规范，包括 Java/Spring 代码质量、设计原则、架构选型、数据库迁移、API 契约、前端组件、架构门禁和 Git 协作。它不声明任何具体产品的模块、租户模型、技术版本、作者、目录或阶段进度；这些固定值由采用本规范的项目 Profile 定义。

## 1. 阅读顺序

1. [Java 代码质量与编码规范](java-code-quality-guidelines.md)
2. [软件设计原则](software-design-principles.md)
3. [设计模式使用规范](design-pattern-guidelines.md)
4. [架构模式选择与功能块实现规范](architecture-pattern-selection.md)
5. [数据结构与算法使用规范](data-structures-and-algorithms.md)
6. [Java 25 语言与运行时规范](java-25-language-and-runtime-guidelines.md)
7. [Java 代码组织规范](java-code-organization.md)
8. [后端架构约束与自动化门禁](backend-architecture-constraints.md)
9. [OpenAPI 与 Controller 开发规范](openapi-and-controller-guidelines.md)
10. [数据库版本与 Migration 开发规范](database-version-and-migration-guidelines.md)
11. [Java Javadoc 规范](java-javadoc-guidelines.md)
12. [Git 提交与协作规范](git-commit-conventions.md)
13. [错误码与前端错误提示开发规范](error-code-handling-guidelines.md)
14. [工程文档体系与维护方法论](documentation-maintenance-methodology.md)

本目录只收录与具体产品无关、任何项目都能直接采用的规范。前端组件规范见
[admin-design](https://github.com/runlume/admin-design)，平台接入契约见
[project/platform-integration.md](../../project/platform-integration.md)。

## 2. 规则优先级

发生冲突时按以下顺序处理：

1. 安全、隐私、数据完整性、隔离边界及已发布外部契约；
2. 采用项目的架构决策、开发 Profile 和当前交付计划；
3. 本目录中的强制规范；
4. 自动格式化器和个人 IDE 偏好。

不能以代码风格规则削弱鉴权、幂等、事务或错误处理。长期偏离架构基线时先更新设计文档或 ADR。一次性的局部例外必须有最小范围、原因、测试和移除条件，不能使用包级宽泛忽略。

## 3. 规范用语

| 用语 | 含义 |
|---|---|
| 必须 / 禁止 | 合并阻断规则；能够自动检查时必须进入 CI。 |
| 应 / 优先 | 默认选择；偏离时在评审中说明具体理由。 |
| 可以 | 经场景判断后使用，不表示默认引入。 |

## 4. 自动化落地原则

- 能由编译器、格式/静态检查、架构测试、契约测试或数据库约束判断的规则，不依赖人工记忆。
- 自动规则必须验证至少扫描到一个目标，不能让空源码或错误包名“绿灯通过”。
- 架构门禁默认放在被检查交付物自己的测试源集；只有多个交付物确需共享扫描时才抽取独立测试组件。
- 规则与实现一起演进；禁止保留已经没有风险对象的僵尸白名单。
- 每个采用项目必须在 `docs/development` 外维护项目 Profile，声明实际技术栈、包、模块、构建任务和例外。

## 5. 变更联动

| 变更 | 同一提交必须更新或验证 |
|---|---|
| 公共 API、OpenAPI、Schema、事件或其他外部契约 | 对应契约、发布说明和契约测试 |
| Controller 路由、参数、响应或错误语义 | OpenAPI 真源、映射方法 Javadoc、契约一致性门禁和生成客户端 |
| 错误码、前端错误提示或供应商失败事实 | 错误码与前端错误提示开发规范、前端翻译、迁移/契约和对应测试 |
| 表、索引、外键、状态事实 | Migration、代码生成输入、数据库集成测试和数据所有权门禁 |
| 包、模块公开面或依赖方向 | 采用项目的模块/架构规则和架构文档 |
| 架构模式、模块职责或功能块实现方式 | 架构模式选择规范；形成新部署或数据所有权边界时补 ADR |
| 自定义算法、缓存或并发结构 | 数据规模上限、复杂度、代表性性能证据和回退条件 |
| 构建、JDK、框架版本 | Wrapper/版本目录、CI 和兼容性验证；长期基线变化按 ADR 管理 |
| 安全、金额、配额或跨系统路由 | 负向测试、审计与失败恢复说明 |
