# Java 25 语言与运行时规范

## 1. 适用范围

本文只约束 Java 25 语言、标准库和 JVM 平台能力的使用，不再承载 SOLID、设计模式、架构模式、集合或算法选型。相关规则分别见[软件设计原则](software-design-principles.md)、[设计模式](design-pattern-guidelines.md)、[架构模式选择](architecture-pattern-selection.md)和[数据结构与算法](data-structures-and-algorithms.md)。

本文适用于已经由项目 Profile 选择 Java 25 作为开发、编译、测试、CI 和生产基线的代码库。新语言/JVM 能力必须解决已有问题，并具有兼容测试、资源上限和回退路径；不能因为“版本支持”就默认启用。

## 2. Java 25 边界

- 主源码、测试、构建和生产运行禁止 `--enable-preview`。
- 禁止 Preview API、Preview 语言特性和 `jdk.incubator.*`。
- Compact Source Files、Module Import Declarations 不用于常规生产应用；生产类型保持显式 package、类型与 import。
- [Structured Concurrency](https://openjdk.org/jeps/505) 在 Java 25 仍是 Preview，不进入主工程。
- [Scoped Values](https://openjdk.org/jeps/506) 在 Java 25 已正式化，但不作为默认领域上下文方案；普通领域和跨模块契约优先显式传参。
- 虚拟线程、ScopedValue、AOT、GC 或对象头优化只有在兼容测试、基准和回退方案齐备后才启用。
- 不为实验特性预建 Source Set、SPI 或基础设施；真实实验需求到来时单独评审。

## 3. 现代 Java 用法

### 3.1 Record 与不可变边界

优先使用 `record` 表达：

- Command、Query、Result、API View；
- Value Object 和复合键，例如 `TenantResourceKey`、`SubjectPermissionKey`；
- 领域事件、Outbox 载荷、不可变配置快照；
- 有类型的允许/拒绝或预占/结算结果。

紧凑构造器校验不变量，对集合使用 `List.copyOf`、`Set.copyOf` 或 `Map.copyOf`。Record 不承担 Aggregate 的可变生命周期，不保存数组、可变集合、Secret、连接、数据库 Record 或惰性 Stream。

```java
public record TenantResourceKey(UUID tenantId, String resourceKey) {
    public TenantResourceKey {
        Objects.requireNonNull(tenantId, "tenantId");
        if (resourceKey == null || resourceKey.isBlank()) {
            throw new IllegalArgumentException("resourceKey must not be blank");
        }
    }
}
```

### 3.2 Sealed Type 与模式匹配

只有类型集合在当前模块内确实封闭时使用 `sealed interface`、record 和穷尽式 `switch`，例如权限决定、额度预占结果和有限状态转换结果。外部 Provider、插件或渠道等开放扩展点不能写入核心 `permits` 列表；它们使用窄 Port，出现两个以上真实实现后才增加 Registry。

模式匹配用于消除重复 cast 并保持穷尽性，不把简单条件改写成难读的类型层级。穷尽 switch 不写掩盖遗漏的 `default`，除非外部枚举存在未知值兼容要求。

### 3.3 `var`、文本块和 switch 表达式

- 局部变量右侧类型明显时可以使用 `var`；不能降低领域含义或让泛型元素类型不可辨。
- 字段、参数和返回值不使用 `var`。
- 文本块适合 SQL、JSON 和测试载荷；生产 SQL 优先项目选定的参数化查询工具，文本块不能拼接不可信输入。
- switch 表达式适合有限映射；复杂状态转换放在领域 Policy 中并测试非法转换。

### 3.4 ScopedValue 与虚拟线程

ScopedValue 只可承载调用范围内不可变、非 Secret 的 `tenantId`、`subjectId`、principal 类型、request/trace ID 等横切快照。禁止存放容器组件、事务、查询上下文、Connection 和可变集合。异步 Operation、Outbox、Delivery 必须显式携带并重新验证上下文，不能假设线程池或回调自动传播。

虚拟线程只用于大量独立阻塞 I/O 且经实测收益明确的场景：

- 每任务创建，不建立固定大小“虚拟线程池”；
- 数据库连接、远程 Provider 和 Webhook 仍有独立并发上限；
- 设置 timeout、取消、异常汇总，禁止 fire-and-forget；
- 不跨线程共享事务、Connection、数据库 Record 或可变领域对象；
- CPU 密集任务使用受限执行器，禁止无依据 `parallelStream()`。

## 4. JVM 与运行时能力门禁

默认使用 JVM 基线配置和 G1。虚拟线程、AOT、非默认 GC、Compact Object Headers、JFR 长期开启、并行计算或新的 JVM Agent 必须提供：代表性负载、CPU/RSS/GC/p95/p99 数据、依赖与 Agent 兼容结果、灰度范围和回退开关。微基准不能单独证明数据库或远程调用链收益。

## 5. 评审清单

- [ ] 未使用 Preview、Incubator、`--enable-preview` 或实验 API。
- [ ] Record/集合在边界防御性复制，无可变或资源对象泄漏。
- [ ] Sealed Type 只用于真实封闭集合，开放扩展点没有写死 `permits`。
- [ ] `var`、文本块、模式匹配和 switch 表达式提升了可读性，没有隐藏业务语义。
- [ ] ScopedValue/虚拟线程没有承载事务、Connection、Secret 或分布式事实。
- [ ] AOT、GC、对象头、JFR 或并发模型变化有基准、兼容验证和回退方案。
