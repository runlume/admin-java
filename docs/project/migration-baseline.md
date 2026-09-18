# 迁移基线与基线收缩记录

> 状态：已执行（2026-09-18），空库等价已验证通过。
> 范围：`src/main/resources/db/migration/`。

## 1. 当前基线

| 文件 | 内容 |
| --- | --- |
| `V001__baseline.sql` | 全部 Schema：表、列、约束、索引与中文 Catalog 注释 |
| `V002__built_in_roles.sql` | 内置角色与权限码种子数据 |
| `V003__catalog_comments.sql` | 收缩后第一个前向迁移：补齐每列 COMMENT，并修正两处已失效的表注释；只写注释，不动结构 |

新增变更从 `V004` 起分配；[数据库版本与迁移规范](../standards/development/database-version-and-migration-guidelines.md)
第 4 节的默认规则：已共享 Migration 不修改、删除、重命名，错误用更高版本前向修复。

这也是模板的对外形状：复制项目时改 `V001` 换业务表、改 `V002` 与 `PermissionCatalog` 换权限，
不必先读完一串增量迁移。

## 2. 收缩内容：把后续迁移回灌到首次建表

| 被吸收的旧迁移 | 去处与处理 |
| --- | --- |
| `V003__platform_membership_revision.sql` | `admin_user.membership_revision` 列、非负 CHECK 与列注释并入 `V001` |
| `V004__workspace_scoped_members.sql` | `admin_user.workspace_id`、`admin_workspace_id_app_instance_uk`、平台账号复合外键、工作区索引与列注释并入 `V001`；空库无行可回填的 `UPDATE` 删除 |
| `V005__role_assignment_source.sql` | `admin_user_role.source` 列、来源 CHECK 与列注释并入 `V001` |
| `V006__permission_code_namespace.sql` | 权限码 CHECK 直接以点分制建立，`admin_role_permission.permission_code` 注释并入 `V001`；种子数据在 `V002` 直接写点分制权限码，旧码改名 `UPDATE` 删除 |
| `V007__local_workspace_source.sql` | `admin_workspace.source` 列、平台字段可空、来源 CHECK、`admin_user_identity_shape_ck` 与 `admin_user_workspace_fk` 并入 `V001`；被替换的 `admin_user_platform_workspace_ck` 不再创建 |

处理原则：

- 表按依赖顺序重排（工作区先于账号），被引用对象建好后再建引用方，因此不再需要"先建表后补约束"的 ALTER。
- 只为兼容既有行而存在的回填与改名语句在空库上是空操作，直接删除而不是保留。
- `V001` 保持收缩前链的最终定义与最终注释文本，不做顺手改写，便于逐项比对等价；历史沿用的表注释
  （`admin_workspace` 写"一对一映射"、`admin_role_permission` 写 `模块:*`）原样保留，随后由 `V003`
  按规范前向修正，而不是回头改 `V001`。

## 3. 等价验证

在一个一次性 PostgreSQL 18.4 容器里分别用两条链建空库，再逐项比对指纹：表、列（含序号、类型、
可空与默认值）、约束（名字与定义）、索引、表与列注释、触发器、函数，以及两张种子表的全部行。

| 对比项 | 结果 |
| --- | --- |
| 旧链 `V001`–`V007` | 指纹 358 行 |
| 新链 `V001`–`V002` | 指纹 358 行，与旧链逐行一致（无差异） |
| jOOQ 生成 | 从新链重建空库后重新生成，表与列定义不变 |
| 新链 `V001`–`V003` | 相对旧链只多出注释：10 张表、83 列、103 条约束、24 个索引与两张种子表全部一致，差异仅 69 条新增列注释与 2 条修正后的表注释 |
| Catalog 注释门禁 | `DatabaseCatalogCommentTests` 断言每张表与每一列都有中文注释；83 列从 14 列有注释补齐到 83 列全覆盖 |
| 应用门禁 | `./gradlew build` 通过（38 项测试、0 失败，含 Testcontainers 集成测试） |

## 4. 这是一次授权的开发期基线重置

本仓库此前已把 `V001`–`V007` 提交进 Git，按规范第 4 节属于"已共享 Migration"；本次收缩是明确
授权的开发期基线重置，不是存量数据库升级：

- 已经跑过旧链的数据库**不能**直接指向新基线，也不需要 `repair`：应备份后按新基线重建，或继续
  在他们自己的分支上按旧链前向演进；
- 仍然禁止通过 `repair`、手工改 `flyway_schema_history`、跳过校验来让旧库"看起来匹配"；
- 本次只使用新建的一次性容器验证，没有改动任何既有开发库或业务库。
