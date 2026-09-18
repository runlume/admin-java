# 文档索引

本目录分两部分：**本项目文档**（`project/`）描述 admin-java 自己的结构与契约；
**内置标准**（`standards/development/`）是从企业私有仓库复制的**通用**开发规范。
平台专有的接入标准不随本仓库公开，原因见第 2.2 节。

## 1. 本项目文档

| 文档 | 内容 |
| --- | --- |
| [project-development-profile.md](project-development-profile.md) | 本项目把通用规范绑定到的技术栈、模块、数据库、接口与门禁值 |
| [project/architecture.md](project/architecture.md) | 模块边界、权限模型、会话与租户隔离、平台接入结构 |
| [project/api.md](project/api.md) | 接口契约、错误码、状态语义与 CSRF 使用方式 |
| [project/platform-integration.md](project/platform-integration.md) | 本仓库实现的最小接入契约：Launch、生命周期、两类 Token、联机探针 |
| [project/template.md](project/template.md) | 作为模板复制时的改动清单与验收项 |

## 2. 内置标准

### 2.1 通用开发规范 `standards/development/`

来源：平台仓库 `docs/development/`，复制时版本 `58446303`。这一套与产品无关、不含平台专有信息，
可以随本仓库公开。它强制适用于本仓库的全部 Java、Gradle、Flyway SQL、OpenAPI 与 Git 改动，
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

本目录唯一的裁剪：平台原第 14 项《前端组件使用规范》未收录。它面向已下线的
Vue / Fantastic Admin 基座，与现行 React + shadcn/ui 前端无关，而本仓库是纯后端交付物。
前端组件规范以 [admin-design](https://github.com/runlume/admin-design) 为准。

### 2.2 平台接入标准不随本仓库公开

平台原有的《SaaS 基座设计、平台能力与业务系统接入》《业务系统平台集成 access 模块标准》
《业务系统登录页平台联机状态接入指南》《业务系统版本与能力契约升级规范》《项目开发约束 Profile》
**没有复制进本仓库**，原因有两条：

1. 它们来自私有仓库 `runlume/platform`，正文包含平台内部信息：控制面的模块全集、关键并发路径的
   内部类与方法名、数据库基线收缩历史、内部测试与约束名，以及产品线命名、商业化边界和阶段路线。
   本仓库是公开仓库，不适合承载这些内容。
2. 其中大部分篇幅描述的是**完整接入**（资源与用量、平台 AI、跨系统能力调用、事件、统一通知、
   文件与私有化 License），而本仓库只实现最小接入，收录反而误导使用者。

本仓库实际实现的接入面由自己维护，见
[project/platform-integration.md](project/platform-integration.md)。需要完整标准时在平台仓库内
按文档名查阅，不要从本仓库转引。

## 3. 同步通用开发规范

在平台仓库更新通用规范后：

```bash
cd admin-java
cp ../platform/docs/development/*.md docs/standards/development/
rm -f docs/standards/development/frontend-component-guidelines.md
```

`rm` 是刻意的：平台仍保留面向旧 Vue 基座的《前端组件使用规范》，本仓库不收录它；
若平台已删除该文件，这一步是无害的空操作。`standards/development/README.md` 的 1.1 节
是本套副本唯一的本地改动，其余文件与平台仓库逐字一致。

平台接入标准不在同步范围内。若通用规范正文引入了指向平台专有文档的跨仓库链接，
在本仓库只保留文字、不带入链接目标，不要为此复制平台文档。

## 4. 本项目固定值

本项目自己的固定值写在 [project-development-profile.md](project-development-profile.md)，
不引用也不复制平台 Profile。通用规范要求每个采用项目维护一份自己的 Profile，本文件即为那一份。
