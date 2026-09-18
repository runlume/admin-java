-- 补齐 Catalog 注释：V001 基线与 V002 种子建立的对象中，仍有列没有 COMMENT，另外两处表注释在
-- 工作区与权限码语义变化后已不准确。本迁移只写注释，不改任何列、约束、索引或数据。
--
-- 注释语言与范围遵循开发 Profile：说明业务含义、单位/范围、可为空的语义或敏感等级。

-- ---------------------------------------------------------------------------
-- Spring Session JDBC
-- ---------------------------------------------------------------------------

COMMENT ON COLUMN public.spring_session.primary_id IS '会话主键，由 Spring Session 生成的主标识';

COMMENT ON COLUMN public.spring_session.session_id IS '浏览器 Cookie 中承载的会话标识，唯一';

COMMENT ON COLUMN public.spring_session.creation_time IS '创建时间（毫秒时间戳）';

COMMENT ON COLUMN public.spring_session.last_access_time IS '最近访问时间（毫秒时间戳），空闲超时据此判断';

COMMENT ON COLUMN public.spring_session.max_inactive_interval IS '空闲超时秒数，超过即视为失效';

COMMENT ON COLUMN public.spring_session.expiry_time IS '绝对过期时间（毫秒时间戳），供会话清理使用';

COMMENT ON COLUMN public.spring_session.principal_name IS '会话主体名；本模板写入本地账号标识，便于按账号撤销会话';

COMMENT ON COLUMN public.spring_session_attributes.session_primary_id IS '所属会话，随会话删除级联清理';

COMMENT ON COLUMN public.spring_session_attributes.attribute_name IS '属性名；Spring Session 的序列化键';

COMMENT ON COLUMN public.spring_session_attributes.attribute_bytes IS '属性值的序列化字节';

-- ---------------------------------------------------------------------------
-- 工作区
-- ---------------------------------------------------------------------------

-- 旧表注释写的是"平台 AppInstance 与本地 workspace 一对一映射"，工作区泛化为自有租户容器后不再准确。
COMMENT ON TABLE public.admin_workspace IS '本系统自有的租户隔离根：PLATFORM 工作区来自平台实例开通，LOCAL 工作区由本系统自建';

COMMENT ON COLUMN public.admin_workspace.id IS '本地工作区标识，业务数据的租户隔离键';

COMMENT ON COLUMN public.admin_workspace.platform_account_id IS '平台 Account 标识；LOCAL 工作区为空';

COMMENT ON COLUMN public.admin_workspace.platform_app_instance_id IS '平台 AppInstance 标识；LOCAL 工作区为空';

COMMENT ON COLUMN public.admin_workspace.module_key IS '平台登记的模块标识；LOCAL 工作区为空';

COMMENT ON COLUMN public.admin_workspace.module_version IS '模块版本；LOCAL 工作区为空';

COMMENT ON COLUMN public.admin_workspace.version IS '并发更新使用的乐观锁版本';

COMMENT ON COLUMN public.admin_workspace.created_at IS '创建时间';

COMMENT ON COLUMN public.admin_workspace.updated_at IS '最近更新时间';

COMMENT ON COLUMN public.admin_workspace.suspended_at IS '暂停时间；未暂停为空';

COMMENT ON COLUMN public.admin_workspace.deprovisioned_at IS '注销时间；未注销为空';

-- ---------------------------------------------------------------------------
-- 账号、角色与授予
-- ---------------------------------------------------------------------------

COMMENT ON COLUMN public.admin_user.id IS '账号标识，同时作为会话主体标识';

COMMENT ON COLUMN public.admin_user.display_name IS '后台展示名称';

COMMENT ON COLUMN public.admin_user.platform_user_id IS '平台用户标识；本地自有账号为空';

COMMENT ON COLUMN public.admin_user.platform_account_id IS '平台 Account 标识；本地自有账号为空';

COMMENT ON COLUMN public.admin_user.platform_app_instance_id IS '平台 AppInstance 标识；本地自有账号为空';

COMMENT ON COLUMN public.admin_user.created_at IS '创建时间';

COMMENT ON COLUMN public.admin_user.updated_at IS '最近更新时间';

COMMENT ON COLUMN public.admin_user.last_login_at IS '最近一次成功登录时间；从未登录为空';

COMMENT ON COLUMN public.admin_role.id IS '角色标识';

COMMENT ON COLUMN public.admin_role.name IS '角色展示名称';

COMMENT ON COLUMN public.admin_role.description IS '角色说明；可为空';

COMMENT ON COLUMN public.admin_role.version IS '并发更新使用的乐观锁版本';

COMMENT ON COLUMN public.admin_role.created_at IS '创建时间';

COMMENT ON COLUMN public.admin_role.updated_at IS '最近更新时间';

-- 旧表注释写的是 `模块:*`，权限码改为点分制后不再准确。
COMMENT ON TABLE public.admin_role_permission IS '角色授予的权限码；`*` 表示全部，`<命名空间>.<资源>.*` 表示资源内全部';

COMMENT ON COLUMN public.admin_role_permission.role_id IS '被授予的角色';

COMMENT ON COLUMN public.admin_user_role.user_id IS '被授予的账号';

COMMENT ON COLUMN public.admin_user_role.role_id IS '授予的角色';

COMMENT ON COLUMN public.admin_user_role.assigned_at IS '授予时间';

-- ---------------------------------------------------------------------------
-- 平台生命周期幂等记录
-- ---------------------------------------------------------------------------

COMMENT ON COLUMN public.admin_lifecycle_operation.id IS '操作标识，平台按它查询异步操作结果';

COMMENT ON COLUMN public.admin_lifecycle_operation.idempotency_key IS '平台传入的幂等键，全表唯一';

COMMENT ON COLUMN public.admin_lifecycle_operation.command IS '生命周期命令：PROVISION、SUSPEND、RESUME、DEPROVISION';

COMMENT ON COLUMN public.admin_lifecycle_operation.request_digest IS '请求摘要；同一幂等键下摘要不同即判定为冲突';

COMMENT ON COLUMN public.admin_lifecycle_operation.state IS '操作状态：PENDING、RUNNING、SUCCEEDED、FAILED';

COMMENT ON COLUMN public.admin_lifecycle_operation.platform_app_instance_id IS '目标平台实例标识；首次建档前为空';

COMMENT ON COLUMN public.admin_lifecycle_operation.external_instance_id IS '返回给平台的不透明实例标识；开通成功后写入';

COMMENT ON COLUMN public.admin_lifecycle_operation.failure_code IS '失败码；成功或进行中为空';

COMMENT ON COLUMN public.admin_lifecycle_operation.created_at IS '创建时间';

COMMENT ON COLUMN public.admin_lifecycle_operation.updated_at IS '最近更新时间';

COMMENT ON COLUMN public.admin_lifecycle_operation.completed_at IS '进入终态的时间；未结束为空';

-- ---------------------------------------------------------------------------
-- 审计
-- ---------------------------------------------------------------------------

COMMENT ON COLUMN public.admin_audit_event.id IS '审计事件标识';

COMMENT ON COLUMN public.admin_audit_event.occurred_at IS '事件发生时间';

COMMENT ON COLUMN public.admin_audit_event.actor_type IS '主体类型：USER、PLATFORM、SYSTEM';

COMMENT ON COLUMN public.admin_audit_event.actor_id IS '主体标识；系统事件可为空';

COMMENT ON COLUMN public.admin_audit_event.action IS '动作码，例如 user.login、member.update';

COMMENT ON COLUMN public.admin_audit_event.target_type IS '目标类型，例如 user、member、notice';

COMMENT ON COLUMN public.admin_audit_event.target_id IS '目标标识；没有具体目标时为空';

COMMENT ON COLUMN public.admin_audit_event.workspace_id IS '所属工作区；非租户事件为空';

COMMENT ON COLUMN public.admin_audit_event.correlation_id IS '请求关联标识，只用于串联日志，不参与授权';

COMMENT ON COLUMN public.admin_audit_event.outcome IS '结果：SUCCESS、FAILURE、DENIED';

-- ---------------------------------------------------------------------------
-- 示例业务域：公告
-- ---------------------------------------------------------------------------

COMMENT ON COLUMN public.admin_notice.id IS '公告标识';

COMMENT ON COLUMN public.admin_notice.title IS '标题，去除首尾空白后 1–200 字符';

COMMENT ON COLUMN public.admin_notice.body IS '正文，最长 20000 字符';

COMMENT ON COLUMN public.admin_notice.status IS '状态：DRAFT、PUBLISHED、ARCHIVED';

COMMENT ON COLUMN public.admin_notice.workspace_id IS '所属工作区；工作区注销时置空';

COMMENT ON COLUMN public.admin_notice.created_by IS '创建者账号';

COMMENT ON COLUMN public.admin_notice.published_at IS '发布时间；未发布为空';

COMMENT ON COLUMN public.admin_notice.version IS '并发更新使用的乐观锁版本';

COMMENT ON COLUMN public.admin_notice.created_at IS '创建时间';

COMMENT ON COLUMN public.admin_notice.updated_at IS '最近更新时间';
