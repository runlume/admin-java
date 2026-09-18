# 错误码与前端错误提示开发规范

> 文档状态：已生效，强制执行  

## 1. 目标与术语

错误码是调用方、前端、运营与异步处理之间的稳定机器标识，不是给用户阅读的错误文案。错误码一经进入 API、事件、操作记录或数据库事实，就不得因实现重构、供应商文案变化或本地化改写而改变含义。

本规范中的“正式含义”是指可由代码注释、契约和前端翻译共同表达的明确业务事实；禁止保留“某某错误码”“临时”“兜底”一类没有语义的占位说明。

## 2. 三类错误码

| 类别 | 用途 | Java 类型 | 放置位置 | 是否对前端可见 |
|---|---|---|---|---|
| API Problem Code | HTTP 请求无法按契约完成时的稳定 Problem 标识 | `<业务>.api.XxxProblemCode` | owning module 的 `api` | 是 |
| Operation Error Code | 异步操作、连通性测试或管理命令的稳定结果 | `<业务>.api.XxxOperationErrorCode` | owning module 的 `api` | 按对应操作视图/契约决定 |
| Internal Failure Code | Application/Adapter 处理或外部调用失败的稳定分类 | `<业务>.application.XxxInternalFailureCode` | owning module 的 `application`；确属跨模块公开面时放 `api` | 不直接作为用户文案 |

- 三类枚举分别实现共享父接口 `ApiProblemCode`、`OperationErrorCode`、`InternalFailureCode`；共享接口只提供类型边界，不承载业务错误码。
- 业务错误码枚举必须放在拥有该错误语义的模块，禁止放到业务根包或无业务归属的 `shared` 包。
- 一个错误事实只保留一个 owning module 的枚举常量。跨模块复用时依赖 owning module 的公开 `api`，不得复制同名常量。
- 枚举常量的 Javadoc 必须说明正式业务含义；API Problem Code 的说明应与默认语言的前端翻译含义一致。

## 3. 后端使用规则

### 3.1 API Problem

- Controller 与 Application 只抛出/映射稳定的 `ApiProblemCode`，不把异常类名、第三方状态码或异常消息作为 `code`。
- `ApiException` 直接接收 `ApiProblemCode` 父接口；调用方传入枚举常量本身，禁止先调用 `.name()` 再传字符串。
- `Problem.code` 必须是枚举常量名。新增、删除或改名属于外部契约变更，必须同步 OpenAPI、前端翻译和兼容性评估。
- 后端可记录受控的诊断上下文，但默认响应不得暴露堆栈、SQL、Secret、远程响应正文或供应商原始错误文案。

```java
throw new ApiException(
        AccountProblemCode.ACCOUNT_NOT_FOUND,
        HttpStatus.NOT_FOUND,
        "当前主体无权访问该 Account。"
);
```

### 3.2 操作与内部失败

- Operation 与 Internal Failure 代码用于状态迁移、审计、重试与诊断，必须表达稳定的失败类别，例如“上游拒绝”“响应无效”“调用超时”。
- 不得把供应商动态码、HTTP 详情、异常消息、URL 或请求载荷转换成枚举常量，也不得拼接进事件类型、操作类型或稳定错误码。
- 需要新增常量时，先判断现有稳定类别是否已经准确覆盖；只有失败事实不同、调用方需要不同处理或审计时才新增。

## 4. 上游原始失败码

外部支付、短信、邮件、文件、AI、Web 或业务 SaaS 可能返回供应商原始失败码。平台必须将“平台稳定分类”与“供应商原始码”分开：

```text
failureCode         = PaymentInternalFailureCode.ALIPAY_REFUND_REJECTED
providerFailureCode = ACQ.SYSTEM_ERROR
```

- `failureCode` 必须为本规范中的稳定枚举；`providerFailureCode` 是可空字符串，不是枚举。
- 当上游确实提供可安全保存的原始码，且该码对运营诊断有消费方时，应在对应失败事实中单独保存 `providerFailureCode`。
- 原始码必须有长度上限和格式/空白校验；数据库列、迁移约束、Store、结果模型和需要展示的受控管理视图同批闭合。
- 原始错误文案、响应正文、请求正文、堆栈、Token、Secret 和用户输入不得作为 `providerFailureCode` 保存或默认返回。需要诊断时保留摘要、受控日志或经审批的脱敏审计事实。
- 上游只返回 HTTP 状态或没有任何失败详情消费方时，使用稳定枚举分类即可；不得为了“可能有用”新增未被读取的原始码字段。

## 5. 前端翻译与托底

- 前端以 `Problem.code` 作为翻译键，从默认语言错误码字典取得面向用户的正式文案；不得将后端 `message`、供应商文案或原始码直接显示给终端用户。
- 每个可由浏览器收到的 API Problem Code 必须在默认语言和项目支持的其他语言中提供翻译；文案改动不改变错误码语义。
- 翻译缺失、响应没有 `code` 或网络/解析失败时，前端必须显示通用托底提示，并保留 traceId（如果响应提供）供支持人员定位。
- 托底提示只说明请求未完成及重试/联系支持的合理动作，不能猜测资源存在性、权限、账单状态或暴露后端内部错误。
- 管理视图如需显示 `providerFailureCode`，必须明确标注为渠道原始码，和用户提示、平台稳定 `failureCode` 分开渲染。

## 6. 契约、数据与测试闭合

| 变更 | 必须同步 |
|---|---|
| 新增或调整 API Problem Code | owning 枚举、前端全部语言翻译、OpenAPI Problem/响应约定、后端映射测试 |
| 新增 Operation/Internal Failure Code | owning 枚举、状态/重试处理、操作或审计视图、直接测试 |
| 保存原始供应商失败码 | Migration、长度和状态约束、Store、结果模型、受控 API Schema（如需展示）、正反向持久化测试 |
| 修改用户可见错误语义 | 前端翻译、提示/弹窗/Toast、契约示例和相关端到端测试 |

- API 契约、Controller 和生成客户端的唯一真源规则按[OpenAPI 与 Controller 开发规范](openapi-and-controller-guidelines.md)执行。
- Migration、状态约束和 PostgreSQL 验证按[数据库版本与 Migration 开发规范](database-version-and-migration-guidelines.md)执行。
- 新增错误路径至少覆盖稳定码、前端翻译命中（或明确托底）以及不得泄露原始文案/敏感载荷的负向场景。

## 7. 评审清单

- [ ] 错误事实已归入 API Problem、Operation 或 Internal Failure 三类之一，且枚举位于 owning module。
- [ ] 枚举注释是正式业务含义，不是常量名复述或临时占位。
- [ ] `ApiException` 直接接收 `ApiProblemCode`，没有字符串 `.name()` 传递。
- [ ] API Problem Code 已有所有支持语言的前端翻译和安全托底。
- [ ] 稳定失败码没有混入供应商动态码、异常消息或响应正文。
- [ ] 上游原始码仅在安全、有消费方的场景以 `providerFailureCode` 单独保存。
- [ ] 迁移、契约、状态约束、测试和调用方已与错误语义同批更新。
