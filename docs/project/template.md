# 作为模板使用

复制本仓库建立新业务系统时的改动清单。目标是让新系统**只改名字、只换业务域**，
不再重新决定会话、权限、租户隔离和平台接入怎么做。

## 1. 必改项

| 顺序 | 位置 | 改动 |
| --- | --- | --- |
| 1 | `settings.gradle` | `rootProject.name` |
| 2 | `build.gradle` | `group`、`description`、`version` |
| 3 | `src/main/resources/application.yml` | `spring.application.name`、`server.servlet.session.cookie.name` |
| 4 | `src/main/resources/banner.txt` | 品牌横幅 |
| 5 | 根包 `app.runlume.admin` | 改成新根包（IDE 重命名后确认 `package-info.java` 一并更新） |
| 6 | `src/jooqCodegen/.../JooqCodegen.java` | 生成包名与每张表的期望清单必须与新根包和新表一致 |
| 7 | `src/main/resources/db/migration/V001__baseline.sql` | 业务表与索引；业务表必须带 workspace 维度 |
| 8 | `PermissionCatalog` + `V002__built_in_roles.sql` | 新权限码与内置角色 |
| 9 | `notice` 包 | 替换为真实业务域 |
| 10 | `README.md`、`AGENTS.md`、`docs/` | 品牌、命令与固定值 |

第 6 步容易被忽略：`JooqCodegen` 会校验实际生成的表清单与期望集合是否完全一致，
表增加了却忘记同步时构建会直接失败，这是刻意设计的门禁。

## 2. 平台接入

保留 `access` 包的骨架，只替换业务语义：

1. `admin.platform.module-id` 填平台上登记的模块标识，`runtime-audience` 与
   `lifecycle-audience` 按 Manifest 填写。
2. 表更新 `accountId`/`appInstanceId` 是 UUID；平台使用其它形态时同步
   `LifecycleController.ProvisionRequest` 与 `admin_workspace` 列类型。
3. `SUSPENDED` 的业务写拦截要落到每个写路径，不能只挡登录。示例中的做法是把工作区状态
   放进会话并在仓储层再次校验。
4. 需要资源、AI、Capability 或 Event 时，按平台侧的完整接入标准增加端口与适配器，
   **不要**在 `access` 里预留空实现。

## 3. 与 admin-design 对接

[admin-design](https://github.com/runlume/admin-design) 是前端标准模板，默认用演示账号独立运行。
对接方式是给它配置 API 基址后改走真实会话，本后端已经提供它需要的全部契约：

| admin-design 需要 | 本后端提供 |
| --- | --- |
| 登录接口 | `POST /api/v1/auth/login`、`POST /api/v1/auth/register` |
| 会话恢复 | `GET /api/v1/me`（含用户与原始权限码） |
| 退出 | `POST /api/v1/auth/logout` |
| CSRF | `GET /api/v1/csrf` + `X-XSRF-TOKEN` 请求头 |
| 权限码 | `*` / `模块:*` / 精确匹配，与前端 `PermissionCode` 约定一致 |

前端把 `src/app/session.ts` 的 `demoAccounts` 换成调用上述接口即可，菜单与按钮的权限过滤不需要改。

## 4. 验收项

复制后至少确认：

- `./gradlew build` 通过（含 Checkstyle、Modulith 门禁、Testcontainers 集成测试）。
- 未登录访问 `/api/v1/me` 返回 401；无 CSRF 的写请求返回 403。
- 首位注册账号获得 `admin`；`GET /api/v1/me` 返回的权限码与数据库角色一致。
- 禁用账号后，该账号既有会话在下次请求即失效。
- 同一 `Idempotency-Key` 重复开通只产生一个 workspace；换请求体再发返回 409。
- `GET /api/v1/platform-connection` 在平台不可达时为 `false`，且响应体只有 `connected`。
- 平台 Launch 建立的会话过期时间不晚于 Context Token 的 `exp`。
