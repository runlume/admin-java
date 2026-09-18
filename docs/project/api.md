# 接口契约

> 通用约定与错误码规范见 [错误码与前端错误提示开发规范](../standards/development/error-code-handling-guidelines.md)
> 与 [OpenAPI 与 Controller 开发规范](../standards/development/openapi-and-controller-guidelines.md)。

## 1. 通用约定

- 请求与响应使用 `application/json`；错误使用 `application/problem+json`。
- 会话是不透明的 `ADMIN_SESSION` Cookie（HttpOnly、SameSite=Lax，生产 Secure），
  浏览器不保存任何访问令牌。
- 所有写请求必须带 CSRF 令牌：先 `GET /api/v1/csrf`，再把返回的 `token` 放进
  `headerName` 指定的请求头（默认 `X-XSRF-TOKEN`）。
- 未认证访问 `/api/**` 返回 `401`；已认证但权限不足返回 `403`。
- 会话所属工作区被暂停或注销后，服务端在每个请求重新校验：id 不一致、工作区不存在或状态
  不再是 `ACTIVE` 时立即失效会话并返回 `401`，前端应回到平台入口重新进入。
- 响应头固定回传 `X-Request-Id`，可用于串联日志；请求可带同名头，但必须匹配
  `[A-Za-z0-9._:-]{8,120}` 才被采纳。

问题响应示例：

```json
{
  "type": "urn:runlume:admin:email-already-registered",
  "title": "EMAIL_ALREADY_REGISTERED",
  "status": 409,
  "detail": "该邮箱已注册",
  "code": "EMAIL_ALREADY_REGISTERED"
}
```

## 2. 会话

### GET /api/v1/csrf

匿名。返回令牌与请求头名称，同时下发 `XSRF-TOKEN` Cookie：

```json
{
  "headerName": "X-XSRF-TOKEN",
  "token": "..."
}
```

### POST /api/v1/auth/register

匿名，受 `admin.local.registration-enabled` 控制。请求体：

```json
{
  "email": "admin@runlume.local",
  "displayName": "系统管理员",
  "password": "runlume-password"
}
```

口令长度 12–200。系统内还没有账号时授予内置 `admin` 角色，否则授予 `member`。成功返回 `201`
并建立会话。邮箱已存在返回 `409 EMAIL_ALREADY_REGISTERED`。

### POST /api/v1/auth/login

匿名。请求体为 `email` 与 `password`，成功返回 `200` 并建立会话。
凭据错误返回 `401 INVALID_CREDENTIALS`，账号被禁用返回 `403 USER_DISABLED`。

### POST /api/v1/auth/logout

需要会话。立即失效服务端会话，返回 `204`。CSRF Cookie 不会随之清除，
前端退登后应重新调用 `GET /api/v1/csrf`。

### GET /api/v1/me

需要会话。返回当前用户与原始权限码：

```json
{
  "user": {
    "id": "00000000-0000-0000-0000-000000000000",
    "email": "admin@runlume.local",
    "displayName": "系统管理员",
    "status": "ACTIVE"
  },
  "roles": ["admin"],
  "permissions": ["*"]
}
```

`permissions` 是原始权限码，格式为 `<module-namespace>.<resource>.<action>`（模板默认
`example.admin`），前端按 `*` / `<命名空间>.<资源>.*` / 精确匹配自行过滤。

### GET /api/v1/permissions

需要会话。返回内置权限目录，供后台渲染权限选择器：

```json
[{"code": "example.admin.member.view", "module": "member", "name": "查看工作区成员"}]
```

## 3. 账号与角色

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/v1/members?page=1&size=20&keyword=` | `example.admin.member.view` | 只列当前工作区成员，返回 `{items,total,page,size}` |
| `GET /api/v1/members/{id}` | `example.admin.member.view` | 成员详情；跨工作区与本地运营会话固定 `404` |
| `PATCH /api/v1/members/{id}` | `example.admin.member.update` | `{displayName?,roles?}`，只改本地角色，平台派生管理员不受影响 |
| `POST /api/v1/members/{id}/status` | `example.admin.member.disable` | `{"status":"ACTIVE"\|"DISABLED"}`，禁用同时撤销该成员全部会话 |
| `GET /api/v1/roles` | `example.admin.role.view` | 角色与权限码 |

`page` 从 1 开始，`size` 上限 100。账号不存在返回 `404 USER_NOT_FOUND`，
引用不存在的角色返回 `400 ROLE_NOT_FOUND`。

## 4. 平台接入

### GET /api/v1/platform-connection

匿名，固定 `Cache-Control: no-store`。响应**只**包含布尔状态：

```json
{"connected": true}
```

`true` 表示后端在超时内取得 HTTP 200、解析出合法 JSON 且 `keys` 为非空数组。
它不表示浏览器已登录、用户已获实例授权或 Launch 链路可用。超时、网络错误、非 200、
非法 JSON 与空密钥集一律为 `false`。

### POST {admin.platform.launch-path}

`application/x-www-form-urlencoded`，表单字段 `code`。由平台以跨站表单提交，
按契约豁免 CSRF；安全性来自 256 位一次性 Code、60 秒有效期与模块绑定。

成功建立本地会话并返回 `303` 跳转到 `actionPath`（无合法路径时跳 `/`）。
平台拒绝返回 `400 LAUNCH_REJECTED`，平台不可用返回 `503 LAUNCH_UNAVAILABLE`，
实例不存在或非 ACTIVE 返回 `404 WORKSPACE_NOT_FOUND` / `403 WORKSPACE_NOT_ACTIVE`。

### 生命周期端点

全部需要平台服务身份 Token，`Issuer`、`Audience` 与逐端点 Scope 由独立安全链校验；
写命令必须带 `Idempotency-Key` 请求头。

| 方法与路径 | Scope | 成功 |
| --- | --- | --- |
| `POST /integration/v1/app-instances` | `instance:provision` | `201 {operationId,externalInstanceId,state}` |
| `GET /integration/v1/operations/{operationId}` | `instance:operation:read` | `200` 操作结果 |
| `POST /integration/v1/app-instances/{id}/suspend` | `instance:suspend` | `200` 操作结果 |
| `POST /integration/v1/app-instances/{id}/resume` | `instance:resume` | `200` 操作结果 |
| `DELETE /integration/v1/app-instances/{id}` | `instance:deprovision` | `200` 操作结果 |

开通请求体：

```json
{
  "accountId": "00000000-0000-0000-0000-000000000000",
  "appInstanceId": "00000000-0000-0000-0000-000000000001",
  "moduleKey": "example.admin",
  "moduleVersion": "1.0.0"
}
```

`SUSPENDED` 与 `DEPROVISIONING` 必须阻止新会话与每次业务写，而不只是隐藏入口。

## 5. 示例业务域：公告

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/v1/notices?page=&size=&status=` | `example.admin.notice.view` | 只返回当前工作区公告；无工作区会话返回 `403 WORKSPACE_REQUIRED` |
| `GET /api/v1/notices/{id}` | `example.admin.notice.view` | 公告详情 |
| `POST /api/v1/notices` | `example.admin.notice.manage` | 新建草稿 |
| `PATCH /api/v1/notices/{id}` | `example.admin.notice.manage` | 仅草稿可改，否则 `409 NOTICE_STATE_INVALID` |
| `POST /api/v1/notices/{id}/status` | `example.admin.notice.manage` | `{"status":"DRAFT"\|"PUBLISHED"\|"ARCHIVED"}` |

## 6. 错误码

| code | HTTP | 含义 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 请求体校验失败 |
| `INVALID_CREDENTIALS` | 401 | 邮箱或口令不正确 |
| `ACCESS_DENIED` | 403 | 权限不足 |
| `USER_DISABLED` | 403 | 账号被禁用 |
| `REGISTRATION_DISABLED` | 403 | 未开放自助注册 |
| `WORKSPACE_NOT_ACTIVE` | 403 | 实例已暂停或注销 |
| `USER_NOT_FOUND` / `WORKSPACE_NOT_FOUND` / `OPERATION_NOT_FOUND` / `NOTICE_NOT_FOUND` | 404 | 目标不存在 |
| `EMAIL_ALREADY_REGISTERED` | 409 | 邮箱已注册 |
| `IDEMPOTENCY_CONFLICT` | 409 | 相同幂等键绑定不同请求 |
| `APP_INSTANCE_ALREADY_REGISTERED` | 409 | 平台实例已被其它账号或模块占用 |
| `NOTICE_STATE_INVALID` | 409 | 公告状态不允许该操作 |
| `WORKSPACE_REQUIRED` | 403 | 当前会话没有工作区，不能访问租户业务数据 |
| `ROLE_NOT_FOUND` / `LAUNCH_REJECTED` | 400 | 角色不存在 / 平台拒绝 Launch |
| `LAUNCH_UNAVAILABLE` / `PLATFORM_UNAVAILABLE` | 503 | 平台或模块服务身份暂不可用 |

新增错误码时同步：异常类型、Problem 处理、本表与前端文案。
