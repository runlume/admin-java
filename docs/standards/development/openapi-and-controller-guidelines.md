# OpenAPI 与 Controller 开发规范

> 文档状态：已生效，强制执行  
> 默认契约模式：Contract First  
> 推荐 OpenAPI 版本：3.1.x  

## 1. 目标与范围

本文约束 HTTP API 的 OpenAPI、Spring MVC Controller、生成客户端和自动化门禁。目标是让 Wire Contract、后端实现和调用方只有一个稳定契约来源，避免注解、YAML 和客户端 DTO 多套定义漂移。

采用项目必须在开发 Profile 中声明契约模式、契约目录、OpenAPI 版本、生成器版本、生成输出、后端/前端构建任务和兼容策略。本文默认推荐 Contract First；采用 Code First 必须由项目 ADR 明确唯一真源，不能局部混用。

Controller 只处理 HTTP 映射、输入校验、可信身份转换、状态码和 Problem 映射；授权、事务和状态机属于 Application/Domain。

## 2. 唯一契约真源

- 契约文件路径由项目 Profile 声明；同一个 API 只能有一个所有者和真源。
- 新增或修改 API 时先修改 OpenAPI，再实现 Controller、契约测试和调用方。
- OpenAPI 声明 Path、Method、稳定唯一 `operationId`、参数、请求体、响应、媒体类型、Schema、鉴权和错误格式。
- `operationId` 发布后属于客户端兼容标识，不能为配合 Java 方法重构随意修改。
- 可信 Principal、租户/资源归属、角色和 Scope 如果来自 Session/Token/服务端绑定，不得伪装成客户端可提交字段。
- Trace Context、Servlet 对象和内部 Request Attribute 不进入外部 OpenAPI Schema。

已发布契约是否不可变、如何升级主版本和如何废弃，由项目兼容策略明确。

## 3. 禁止第二套契约

采用 Contract First 时：

- 生产 Controller、传输类型和领域类型不使用 Swagger/OpenAPI 描述注解复制 YAML；
- 不通过运行时扫描反向覆盖契约文件；
- `@Operation`、`@ApiResponse`、`@Parameter`、`@Schema` 等不能成为第二真源；
- Swagger UI 只能读取已提交并校验的静态契约；
- 改为 Code First 属于架构决策，必须先定义发布、兼容、生成和迁移方案。

框架为了运行时路由或校验需要的非描述性注解可以使用，但不能承载另一份业务 Schema。

## 4. Controller 映射方法 Javadoc

### 4.1 必写范围

采用项目应要求所有生产 Web 映射方法在映射注解前提供 Javadoc，不受 Java 可见性影响。Spring MVC 包括 `@RequestMapping`、`@GetMapping`、`@PostMapping`、`@PutMapping`、`@PatchMapping` 和 `@DeleteMapping`。

类级 Javadoc 不能替代方法级 Javadoc。方法通常不添加类级作者/版本标签，具体由[Javadoc 规范](java-javadoc-guidelines.md)和项目 Profile 决定。

### 4.2 注释职责

方法 Javadoc 至少有用途摘要，并按实际复杂度说明：

- 调用主体和可信身份来源；
- 租户、资源、Provider 等归属如何从可信上下文取得；
- 幂等键、重复、同键不同载荷和结果未知语义；
- 本地事务与远程副作用的先后；
- 非显然的分页、稳定顺序、硬上限和部分成功；
- 调用者需要处理的稳定领域异常或安全拒绝。

Java 参数按项目 Javadoc 规则提供 `@param`，包括框架注入的可信 Principal 或 Trace Context；这些内部参数不能因此进入外部契约。

### 4.3 不重复 OpenAPI

Javadoc 不复制完整 URL、Method、媒体类型、全量响应码、JSON 示例和 DTO Schema。OpenAPI 负责 Wire Contract；Javadoc 负责实现者需要理解但签名无法表达的信任、事务、幂等和隔离语义。

### 4.4 示例

```java
/**
 * 分页查询当前主体可见的资源。
 *
 * <p>主体来自已验证会话。查询条件只过滤已获授权的数据，不能扩大可见范围。</p>
 *
 * @param principal 当前已认证主体；不可为 {@code null}
 * @param limit 单页数量；不得超过项目硬上限
 * @param cursor 不透明分页游标；首页可以为空
 * @param query 搜索词；可以为空
 * @return 按稳定顺序排列的资源分页结果
 */
@GetMapping
PageResult<ResourceView> listResources(
        AuthenticatedPrincipal principal,
        Integer limit,
        String cursor,
        String query
) {
    // ...
}
```

## 5. Contract First 开发流程

一次 API 变更按顺序闭合：

1. 修改 owning OpenAPI，补齐 `operationId`、Schema、状态码、Problem 和安全要求；
2. 执行结构校验和兼容检查；GET/HEAD 不声明请求体，列表有分页和硬上限；
3. 从契约生成客户端或构建期制品，生成目录不提交、不手工修改；
4. 实现/修改 Controller，并编写方法 Javadoc；
5. 更新 Application、传输类型和契约测试；
6. 调用方使用生成 Client，禁止维护第二套路径和传输 DTO；
7. 执行契约/实现一致性、后端检查、客户端编译和真实 API Smoke。

存量手写 Client 的迁移应由项目计划明确；新 API 不能继续扩大分叉。

服务端可以从 YAML 生成 DTO、接口或验证制品，也可以手写 Controller。不能为了适配生成接口把可信身份和服务端上下文改成客户端参数。

## 6. 生成物规则

- 生成器和插件版本由构建锁定，不使用开发者机器上的浮动全局版本。
- 生成物写入构建/临时目录，不提交，不进入手写源码的格式、Javadoc 或覆盖率门禁。
- 编译/测试显式依赖生成任务；缺少契约、生成失败或无法编译时失败关闭。
- 不在生成物外维护同名传输 DTO；领域值对象在边界显式转换。
- 生成 Client 不包含服务端内部 Principal、Secret、Trace Context 或可覆盖的资源归属字段。
- 多份契约合并时检测 `operationId` 和 Schema 名冲突，禁止静默覆盖。

## 7. 自动化门禁

采用项目的统一后端/前端检查至少覆盖：

1. OpenAPI 结构合法，引用闭包存在且无不受控网络 `$ref`；
2. 每个 Operation 有唯一、稳定 `operationId`；
3. GET/HEAD 无请求体，路径参数、请求体和响应符合 HTTP 语义；
4. 实际映射和未废弃 Operation 双向存在；
5. 映射方法 Javadoc 与 Java 参数一致；
6. Contract First 项目不依赖描述注解或运行时生成器；
7. 生成 Client 可编译且无手写传输契约分叉；
8. 扫描目标为空、映射归属未知或故意违规夹具未被拒绝时失败。

一致性门禁至少比较 Method、规范化 Path、参数位置、请求体存在性和成功响应。状态码、Problem、鉴权与 Schema 由契约测试/真实请求补足。

## 8. 兼容与版本

- 新增可选字段、可选参数或新 Operation 通常向后兼容；删除/重命名、改变类型/必填性/身份方式/路径通常是破坏性变化。
- 破坏性变化使用新主版本或项目兼容策略定义的迁移窗口，不能原地覆盖已发布契约。
- Java 方法名可以重构，但不能无意改变已发布 `operationId`、Wire Path 或 Schema。
- 生成 Client 升级必须通过编译和真实调用 Smoke；“生成成功”不等于兼容。

## 9. 评审清单

- [ ] 项目 Profile 已声明契约模式、目录、版本、生成器、构建和兼容策略。
- [ ] 先修改唯一契约，`operationId` 唯一稳定。
- [ ] 没有注解或运行时反向生成的第二套契约。
- [ ] 映射方法有合格 Javadoc，且位于映射注解之前。
- [ ] Javadoc 说明信任、隔离、幂等和失败，不复制 Wire Contract。
- [ ] 服务端可信上下文没有伪装成客户端输入。
- [ ] 列表分页、搜索、排序和响应大小有硬上限。
- [ ] 契约/实现双向一致，生成 Client 编译门禁通过。
- [ ] 契约、Controller、测试、调用方和兼容文档同批闭合。
