# 文档索引

本目录分两部分：**本项目文档**（`project/`）描述 admin-java 自己的结构与契约；
**内置标准**（`standards/development/`）是与产品无关、可以直接采用的**通用**开发规范。

## 1. 本项目文档

| 文档 | 内容 |
| --- | --- |
| [project-development-profile.md](project-development-profile.md) | 本项目把通用规范绑定到的技术栈、模块、数据库、接口与门禁值 |
| [project/architecture.md](project/architecture.md) | 模块边界、权限模型、会话与租户隔离、平台接入结构 |
| [project/api.md](project/api.md) | 接口契约、错误码、状态语义与 CSRF 使用方式 |
| [project/platform-integration.md](project/platform-integration.md) | 本仓库实现的最小接入契约：Launch、生命周期、两类 Token、联机探针 |
| [project/platform-capabilities.md](project/platform-capabilities.md) | 平台开放能力与 SDK 接口：有哪些能力、入口在哪、接入前置 |
| [project/template.md](project/template.md) | 作为模板复制时的改动清单与验收项 |

## 2. 内置标准

### 2.1 通用开发规范 `standards/development/`

这一套规范强制适用于本仓库的全部 Java、Gradle、Flyway SQL、OpenAPI 与 Git 改动，
入口是 [standards/development/README.md](standards/development/README.md)。重点：

| 文档 | 何时必读 |
| --- | --- |
| [java-javadoc-guidelines.md](standards/development/java-javadoc-guidelines.md) | 新增任何生产类型 |
| [java-25-language-and-runtime-guidelines.md](standards/development/java-25-language-and-runtime-guidelines.md) | 使用新语言特性或并发结构 |
| [java-code-organization.md](standards/development/java-code-organization.md) | 新增包或顶层类型 |
| [backend-architecture-constraints.md](standards/development/backend-architecture-constraints.md) | 改动模块边界或依赖方向 |
| [database-version-and-migration-guidelines.md](standards/development/database-version-and-migration-guidelines.md) | 改 Flyway Migration、SQL 或 jOOQ |
| [openapi-and-controller-guidelines.md](standards/development/openapi-and-controller-guidelines.md) | 新增或修改 Controller 与 HTTP 契约 |
| [error-code-handling-guidelines.md](standards/development/error-code-handling-guidelines.md) | 新增错误码或前端错误提示 |
| [git-commit-conventions.md](standards/development/git-commit-conventions.md) | 提交前 |

其余为设计原则与模式选型规范，按需查阅。

## 3. 本项目固定值

本项目自己的固定值写在 [project-development-profile.md](project-development-profile.md)；
通用规范要求每个采用项目维护一份自己的 Profile，本文件即为那一份。
