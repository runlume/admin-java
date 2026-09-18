# Java 代码质量与编码规范

> 文档状态：已生效，强制执行  
> 适用范围：全部 Java 主源码与测试源码  

## 1. 目标

本文负责“代码看起来和读起来是否一致”：命名、格式、控制流、Lambda、注释、异常、日志、空值、不可变性和测试可读性。包、层和模块归属见[Java 代码组织规范](java-code-organization.md)；设计与架构判断不在本文重复。

规则优先顺序是正确性与安全、可读性、一致性、简洁性。不能为追求短代码压缩异常处理、租户校验或数据完整性。

## 2. 命名

| 对象 | 规则 | 示例 |
|---|---|---|
| 类型 | PascalCase，使用准确领域名 | `SubscriptionProvisioningService` |
| 方法 | camelCase，以动作或判断开头 | `reserveUsage`、`isAccessAllowed` |
| 变量/参数 | camelCase，表达业务含义和单位 | `leaseDuration`、`amountInMinorUnit` |
| 常量 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| Controller | `*Controller` | `SubscriptionController` |
| 应用服务 | `*ApplicationService` 或具体用例名 | `AccessGrantApplicationService` |
| 外部适配器 | `*Adapter`，包含协议或供应商 | `PaymentProviderAdapter` |
| 查询/存储 | 以用途命名 | `ActiveSubscriptionQuery`、`UsageReservationStore` |
| Command/Query/View | 后缀表达方向 | `ProvisionSubscriptionCommand` |
| 布尔值 | `is/has/can/should` 表达判断 | `isActive`、`canRetry` |

- 禁止无业务含义的 `a`、`tmp`、`data1`、`obj`、`flag`、`list2`；极短局部循环索引 `i` 可以使用。
- 不使用 `Manager`、`Helper`、`Processor`、`Common`、`Util` 等含糊名称，除非职责能够被该名称准确限定。
- 缩写按普通单词处理：`OidcClient`、`JwksCache`、`ApiToken`，不写 `OIDCClient`。
- 名称不重复类型已经表达的信息，例如 `List<Customer> customerList` 优先写 `customers`。

## 3. 格式与控制流

- UTF-8、LF、4 个空格、文件末尾换行，不使用 Tab。
- 一个公开顶层类型一个文件；文件名与公开类型一致；一行一个语句。
- 花括号使用 K&R 风格；所有 `if`、`else`、`for`、增强 `for`、`while` 和 `do` 语句体必须有 `{}`。
- 成员顺序：常量、字段、构造器、公开方法、包可见/受保护方法、私有方法、嵌套类型。
- 使用构造器注入和 `final` 字段，禁止 Spring 字段注入。
- 禁止通配 import，删除未使用 import，不提交与目标无关的整文件格式化。
- 单行优先保持在 100～120 字符内；URL、不可拆分标识和生成源码可以例外。换行必须提升可读性，不能只为满足数字制造锯齿代码。
- 早返回用于减少无意义嵌套，但不能绕过统一审计、资源释放或事务收尾。

Checkstyle 至少自动阻断 Tab、无文件末尾换行、通配 import、单行多语句、文件名不一致和无花括号控制语句。禁止使用全局 suppression 绕过手写源码。

## 4. Lambda、Stream 与普通循环

- 无状态、短小的函数式接口实现必须使用 Lambda；简单委托优先方法引用。
- 自定义函数式接口必须标注 `@FunctionalInterface`。
- Stream 只用于短的映射、过滤、分组和归约，不包含数据库/网络 I/O、事务、重试、锁或多次副作用。
- 复杂分支、可中断处理、细粒度恢复和有顺序的副作用使用有花括号的普通循环或命名方法。
- Collector 遇到业务上非法的重复键必须失败；禁止 `(left, right) -> left` 静默吞掉冲突。
- 不使用无依据的 `parallelStream()`；并行策略必须有资源上限和性能证据。

```java
var activeIds = subscriptions.stream()
    .filter(Subscription::isActive)
    .map(Subscription::id)
    .toList();

for (Subscription subscription : subscriptions) {
    store.lockAndUpdate(subscription.id());
}
```

## 5. 注释与 Javadoc

- 注释解释“为什么、边界和失败”，不逐行翻译代码。
- 生产顶层类型、公开契约、Controller 映射方法和安全关键算法按[Java Javadoc 规范](java-javadoc-guidelines.md)执行。
- Controller Javadoc 与 OpenAPI 的分工按[OpenAPI 与 Controller 开发规范](openapi-and-controller-guidelines.md)执行。
- 普通行注释不能替代应由类型、约束、测试或 Javadoc 表达的契约。
- TODO 必须关联明确工作包并写移除条件；安全校验、数据修复和失败恢复不能留作无 Owner TODO。
- 禁止在注释、示例和日志中放真实账号、Token、Secret、私钥、客户数据或生产 URL。

## 6. 异常与错误处理

- 领域拒绝使用有稳定错误码的明确异常或结果类型；HTTP 统一映射为 `application/problem+json`。
- 禁止吞异常、空 `catch`、返回 `null` 表示失败，或 `catch (Exception)` 后记录一行继续运行。
- 只捕获当前层能够处理、转换或补充上下文的异常；否则让异常传播到统一边界。
- 包装技术异常时保留 cause，并增加非敏感 operation、module、tenant/resource 引用等诊断上下文。
- 资源使用 try-with-resources；中断异常必须恢复中断标志或显式传播，不能吞掉。
- 开发环境对未预期异常在控制台打印完整堆栈和 Trace ID；生产响应不暴露堆栈、SQL、内部类名或 Secret，生产日志仍保留受控诊断信息。
- 重试只处理已证明可重试且幂等的失败；次数、退避、抖动、总时限和结果未知路径必须明确。

## 7. 日志

- 使用 SLF4J 参数化日志，不使用字符串拼接，不直接调用 `System.out/err`。
- `ERROR` 表示需要人工或自动告警关注的失败；`WARN` 表示可恢复但异常的状态；正常业务拒绝不滥用 `ERROR`。
- 同一异常只在负责处理或终止请求的边界记录一次，避免每层重复打印堆栈。
- 日志包含 Trace ID 和必要的非敏感业务引用；禁止 Token、Cookie、密码、密钥、Prompt、邮件正文、通知正文和完整个人信息。
- 循环、重试和批处理日志必须限频或聚合，不能形成日志放大。

## 8. 空值、类型与不可变性

- `Optional` 只用于可能缺失的返回值，不作为字段、参数、请求模型或集合元素；禁止 `Optional.get()`。
- 集合返回空集合而不是 `null`；输入使用 Bean Validation、紧凑构造器或前置条件尽早失败。
- 优先不可变 `record`、`List.copyOf`、`Set.copyOf`、`Map.copyOf` 和 `final` 字段。
- 不在 record 或跨模块 API 中保存可变数组、集合、持久化 Record、Connection、惰性 Stream 或 Secret。
- 枚举使用 `==`；双方可空使用 `Objects.equals`；不要用字符串模拟封闭状态。
- 外部不透明 ID 不在业务深层随意解析；容易混淆的 Tenant/Subject/Resource 标识使用明确值类型。

## 9. 测试代码质量

- 测试包镜像生产包；测试类使用 `*Test`，数据库/外部组合测试使用 `*IntegrationTest`。
- 测试名表达条件和行为，例如 `rejectsAccessWhenSubscriptionIsSuspended`，不使用 `works`、`test1`。
- 使用 Arrange/Act/Assert 的清晰结构，但不要求添加机械分段注释。
- 不使用 `Thread.sleep` 等待异步结果；使用可控 `Clock`、有上限轮询或同步测试端口。
- 不向生产代码加入测试后门；Fake、Stub 和 Fixture 只存在于测试源码/测试交付物。
- 状态机、金额、额度、幂等、隔离和安全路径覆盖正向、边界与负向。

## 10. 版本控制

分支、提交、暂存、复核和推送规则见[Git 提交与协作规范](git-commit-conventions.md)。具体主分支、远端和评审流程由采用项目声明，不机械套用 Git Flow。

## 11. 评审清单

- [ ] 命名表达领域含义、单位和状态，没有模糊缩写或万能名称。
- [ ] 格式、花括号、成员顺序和 import 符合统一规则。
- [ ] Lambda/Stream 只处理纯转换，副作用使用明确控制流。
- [ ] 注释解释原因和边界，Javadoc/OpenAPI 没有互相复制或冲突。
- [ ] 异常没有被吞掉，开发日志可诊断，生产响应和日志不泄密。
- [ ] Optional、null、集合和可变对象没有跨边界扩散。
- [ ] 测试名称、结构、时钟和失败路径可重复验证。
- [ ] Commit 内聚且不包含无关格式化、生成物或本地配置。
