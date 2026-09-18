-- Runlume 标准后台后端基线：服务端会话、本地身份、角色权限、工作区、生命周期操作、审计与示例业务域。
--
-- 本文件是收缩后的 Schema 基线：后补的列、约束、索引与注释都已回灌到首次建表，不再保留
-- "先建不完整表再用 ALTER 补齐" 的中间态。空库执行本文件与 V002 种子后，Schema 与种子数据
-- 等价于收缩前的 V001–V007 链。

-- ---------------------------------------------------------------------------
-- Spring Session JDBC：浏览器只持有不透明会话 Cookie，服务端保存会话事实。
-- ---------------------------------------------------------------------------

CREATE TABLE public.spring_session (
    primary_id character(36) NOT NULL,
    session_id character(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name character varying(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

COMMENT ON TABLE public.spring_session IS 'Spring Session 管理的服务端浏览器会话';

COMMENT ON COLUMN public.spring_session.primary_id IS '会话主键，由 Spring Session 生成的主标识';

COMMENT ON COLUMN public.spring_session.session_id IS '浏览器 Cookie 中承载的会话标识，唯一';

COMMENT ON COLUMN public.spring_session.creation_time IS '创建时间（毫秒时间戳）';

COMMENT ON COLUMN public.spring_session.last_access_time IS '最近访问时间（毫秒时间戳），空闲超时据此判断';

COMMENT ON COLUMN public.spring_session.max_inactive_interval IS '空闲超时秒数，超过即视为失效';

COMMENT ON COLUMN public.spring_session.expiry_time IS '绝对过期时间（毫秒时间戳），供会话清理使用';

COMMENT ON COLUMN public.spring_session.principal_name IS '会话主体名；本模板写入本地账号标识，便于按账号撤销会话';

CREATE UNIQUE INDEX spring_session_ix1 ON public.spring_session USING btree (session_id);

CREATE INDEX spring_session_ix2 ON public.spring_session USING btree (expiry_time);

CREATE INDEX spring_session_ix3 ON public.spring_session USING btree (principal_name);

CREATE TABLE public.spring_session_attributes (
    session_primary_id character(36) NOT NULL,
    attribute_name character varying(200) NOT NULL,
    attribute_bytes bytea NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES public.spring_session (primary_id) ON DELETE CASCADE
);

COMMENT ON TABLE public.spring_session_attributes IS 'Spring Session 会话的序列化属性';

COMMENT ON COLUMN public.spring_session_attributes.session_primary_id IS '所属会话，随会话删除级联清理';

COMMENT ON COLUMN public.spring_session_attributes.attribute_name IS '属性名；Spring Session 的序列化键';

COMMENT ON COLUMN public.spring_session_attributes.attribute_bytes IS '属性值的序列化字节';

-- ---------------------------------------------------------------------------
-- 工作区：所有业务数据的租户隔离根，来源分 PLATFORM（平台开通）与 LOCAL（本系统自建）。
-- 先建本表，账号与示例业务域的归属外键才能在建表时就位。
-- ---------------------------------------------------------------------------

CREATE TABLE public.admin_workspace (
    id uuid NOT NULL,
    platform_account_id uuid,
    platform_app_instance_id uuid,
    module_key character varying(120),
    module_version character varying(64),
    external_instance_id character varying(120),
    status character varying(32) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    updated_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    suspended_at timestamp with time zone,
    deprovisioned_at timestamp with time zone,
    source character varying(16) DEFAULT 'PLATFORM' NOT NULL,
    CONSTRAINT admin_workspace_pk PRIMARY KEY (id),
    CONSTRAINT admin_workspace_id_app_instance_uk UNIQUE (id, platform_app_instance_id),
    CONSTRAINT admin_workspace_app_instance_uk UNIQUE (platform_app_instance_id),
    CONSTRAINT admin_workspace_external_uk UNIQUE (external_instance_id),
    CONSTRAINT admin_workspace_status_ck CHECK (
        status IN ('ACTIVE', 'SUSPENDED', 'DEPROVISIONING', 'DEPROVISIONED')
    ),
    CONSTRAINT admin_workspace_version_ck CHECK (version >= 0),
    CONSTRAINT admin_workspace_source_ck CHECK (
        (
            source = 'PLATFORM'
            AND platform_account_id IS NOT NULL
            AND platform_app_instance_id IS NOT NULL
            AND module_key IS NOT NULL
            AND module_version IS NOT NULL
        )
        OR (
            source = 'LOCAL'
            AND platform_account_id IS NULL
            AND platform_app_instance_id IS NULL
            AND module_key IS NULL
            AND module_version IS NULL
            AND external_instance_id IS NULL
        )
    )
);

COMMENT ON TABLE public.admin_workspace IS '本系统自有的租户隔离根：PLATFORM 工作区来自平台实例开通，LOCAL 工作区由本系统自建';

COMMENT ON COLUMN public.admin_workspace.id IS '本地工作区标识，业务数据的租户隔离键';

COMMENT ON COLUMN public.admin_workspace.platform_account_id IS '平台 Account 标识；LOCAL 工作区为空';

COMMENT ON COLUMN public.admin_workspace.platform_app_instance_id IS '平台 AppInstance 标识；LOCAL 工作区为空';

COMMENT ON COLUMN public.admin_workspace.module_key IS '平台登记的模块标识；LOCAL 工作区为空';

COMMENT ON COLUMN public.admin_workspace.module_version IS '模块版本；LOCAL 工作区为空';

COMMENT ON COLUMN public.admin_workspace.external_instance_id IS '返回给平台的不透明实例标识，平台不得解析其结构';

COMMENT ON COLUMN public.admin_workspace.status IS 'SUSPENDED 必须阻止新会话与每次业务写，而不只是隐藏入口';

COMMENT ON COLUMN public.admin_workspace.version IS '并发更新使用的乐观锁版本';

COMMENT ON COLUMN public.admin_workspace.created_at IS '创建时间';

COMMENT ON COLUMN public.admin_workspace.updated_at IS '最近更新时间';

COMMENT ON COLUMN public.admin_workspace.suspended_at IS '暂停时间；未暂停为空';

COMMENT ON COLUMN public.admin_workspace.deprovisioned_at IS '注销时间；未注销为空';

COMMENT ON COLUMN public.admin_workspace.source IS '工作区来源：PLATFORM 由平台实例开通创建，LOCAL 由本系统自建';

-- ---------------------------------------------------------------------------
-- 本地身份：业务系统自己持有的账号、角色与权限。
-- ---------------------------------------------------------------------------

CREATE TABLE public.admin_user (
    id uuid NOT NULL,
    email character varying(320) NOT NULL,
    display_name character varying(200) NOT NULL,
    password_hash character varying(200),
    credential_source character varying(32) NOT NULL,
    platform_user_id uuid,
    platform_account_id uuid,
    platform_app_instance_id uuid,
    status character varying(32) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    updated_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    last_login_at timestamp with time zone,
    membership_revision bigint DEFAULT 0 NOT NULL,
    workspace_id uuid,
    CONSTRAINT admin_user_pk PRIMARY KEY (id),
    CONSTRAINT admin_user_email_ck CHECK (
        email = lower(email) AND email ~ '^[^[:space:]@]+@[^[:space:]@]+$'
    ),
    CONSTRAINT admin_user_status_ck CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT admin_user_version_ck CHECK (version >= 0),
    CONSTRAINT admin_user_membership_revision_ck CHECK (membership_revision >= 0),
    CONSTRAINT admin_user_credential_ck CHECK (
        (
            credential_source = 'LOCAL'
            AND password_hash IS NOT NULL
            AND platform_user_id IS NULL
            AND platform_account_id IS NULL
            AND platform_app_instance_id IS NULL
        )
        OR (
            credential_source = 'PLATFORM'
            AND password_hash IS NULL
            AND platform_user_id IS NOT NULL
            AND platform_account_id IS NOT NULL
            AND platform_app_instance_id IS NOT NULL
        )
    ),
    -- 账号形状：平台账号必须绑定工作区，本地账号可以绑定自有工作区，也可以还没有（自举）。
    CONSTRAINT admin_user_identity_shape_ck CHECK (
        (
            credential_source = 'LOCAL'
            AND password_hash IS NOT NULL
            AND platform_user_id IS NULL
            AND platform_account_id IS NULL
            AND platform_app_instance_id IS NULL
        )
        OR (
            credential_source = 'PLATFORM'
            AND password_hash IS NULL
            AND platform_user_id IS NOT NULL
            AND platform_account_id IS NOT NULL
            AND platform_app_instance_id IS NOT NULL
            AND workspace_id IS NOT NULL
        )
    ),
    -- 平台账号的工作区必须与其平台实例映射一致；本地账号因平台实例为空不受复合外键约束。
    CONSTRAINT admin_user_workspace_instance_fk FOREIGN KEY (
        workspace_id,
        platform_app_instance_id
    ) REFERENCES public.admin_workspace (id, platform_app_instance_id),
    -- 本地账号的工作区归属需要独立外键：复合外键在本地账号上不生效。
    CONSTRAINT admin_user_workspace_fk FOREIGN KEY (workspace_id)
        REFERENCES public.admin_workspace (id)
);

COMMENT ON TABLE public.admin_user IS '本地后台账号；LOCAL 为自有密码账号，PLATFORM 为平台 Launch 映射出的影子账号';

COMMENT ON COLUMN public.admin_user.id IS '账号标识，同时作为会话主体标识';

COMMENT ON COLUMN public.admin_user.email IS '规范化为小写的登录邮箱，同时作为用户唯一标识';

COMMENT ON COLUMN public.admin_user.display_name IS '后台展示名称';

COMMENT ON COLUMN public.admin_user.password_hash IS 'BCrypt 口令摘要；平台映射账号不保存口令';

COMMENT ON COLUMN public.admin_user.credential_source IS '凭据来源：LOCAL 自有口令，PLATFORM 平台 Launch';

COMMENT ON COLUMN public.admin_user.platform_user_id IS '平台用户标识；本地自有账号为空';

COMMENT ON COLUMN public.admin_user.platform_account_id IS '平台 Account 标识；本地自有账号为空';

COMMENT ON COLUMN public.admin_user.platform_app_instance_id IS '平台 AppInstance 标识；本地自有账号为空';

COMMENT ON COLUMN public.admin_user.status IS 'DISABLED 时拒绝登录并立即失效既有会话';

COMMENT ON COLUMN public.admin_user.version IS '并发更新使用的乐观锁版本';

COMMENT ON COLUMN public.admin_user.created_at IS '创建时间';

COMMENT ON COLUMN public.admin_user.updated_at IS '最近更新时间';

COMMENT ON COLUMN public.admin_user.last_login_at IS '最近一次成功登录时间；从未登录为空';

COMMENT ON COLUMN public.admin_user.membership_revision IS
    '平台实例成员授权修订号；本地自有账号固定为 0，平台映射账号只允许单调递增';

COMMENT ON COLUMN public.admin_user.workspace_id IS
    '平台账号所属本地工作区；本地运营账号为 NULL，且必须与其平台实例映射一致';

-- 只有自有口令账号才要求邮箱唯一：平台映射账号的身份唯一性由
-- (platform_app_instance_id, platform_user_id) 保证，允许同名邮箱并存，避免
-- 平台 Launch 与本地账号因邮箱碰撞而互相接管。
CREATE UNIQUE INDEX admin_user_local_email_uk
    ON public.admin_user (email)
    WHERE credential_source = 'LOCAL';

CREATE UNIQUE INDEX admin_user_platform_uk
    ON public.admin_user (platform_app_instance_id, platform_user_id)
    WHERE platform_user_id IS NOT NULL;

CREATE INDEX admin_user_workspace_ix
    ON public.admin_user USING btree (workspace_id);

CREATE TABLE public.admin_role (
    id uuid NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(300),
    built_in boolean DEFAULT false NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    updated_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    CONSTRAINT admin_role_pk PRIMARY KEY (id),
    CONSTRAINT admin_role_code_uk UNIQUE (code),
    CONSTRAINT admin_role_code_ck CHECK (code ~ '^[a-z][a-z0-9-]{1,63}$'),
    CONSTRAINT admin_role_version_ck CHECK (version >= 0)
);

COMMENT ON TABLE public.admin_role IS '本地业务角色；平台实例角色与本表分离，不互相覆盖';

COMMENT ON COLUMN public.admin_role.id IS '角色标识';

COMMENT ON COLUMN public.admin_role.code IS '稳定角色码，业务规则和契约引用它而不是名称';

COMMENT ON COLUMN public.admin_role.name IS '角色展示名称';

COMMENT ON COLUMN public.admin_role.description IS '角色说明；可为空';

COMMENT ON COLUMN public.admin_role.built_in IS '内置角色不允许删除，避免运维误锁死后台';

COMMENT ON COLUMN public.admin_role.version IS '并发更新使用的乐观锁版本';

COMMENT ON COLUMN public.admin_role.created_at IS '创建时间';

COMMENT ON COLUMN public.admin_role.updated_at IS '最近更新时间';

CREATE TABLE public.admin_role_permission (
    role_id uuid NOT NULL,
    permission_code character varying(100) NOT NULL,
    CONSTRAINT admin_role_permission_pk PRIMARY KEY (role_id, permission_code),
    CONSTRAINT admin_role_permission_role_fk FOREIGN KEY (role_id)
        REFERENCES public.admin_role (id) ON DELETE CASCADE,
    CONSTRAINT admin_role_permission_code_ck CHECK (
        permission_code ~ '^(\*|[a-z0-9]+(\.[a-z0-9]+){2,}(\.\*)?)$'
    )
);

COMMENT ON TABLE public.admin_role_permission IS '角色授予的权限码；`*` 表示全部，`<命名空间>.<资源>.*` 表示资源内全部';

COMMENT ON COLUMN public.admin_role_permission.role_id IS '被授予的角色';

COMMENT ON COLUMN public.admin_role_permission.permission_code IS
    '权限码：`*` 表示全部，`<命名空间>.<资源>.*` 表示资源内全部，其余精确匹配';

CREATE TABLE public.admin_user_role (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    assigned_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    source character varying(16) DEFAULT 'LOCAL' NOT NULL,
    CONSTRAINT admin_user_role_pk PRIMARY KEY (user_id, role_id),
    CONSTRAINT admin_user_role_user_fk FOREIGN KEY (user_id)
        REFERENCES public.admin_user (id) ON DELETE CASCADE,
    CONSTRAINT admin_user_role_role_fk FOREIGN KEY (role_id)
        REFERENCES public.admin_role (id) ON DELETE CASCADE,
    CONSTRAINT admin_user_role_source_ck CHECK (source IN ('LOCAL', 'PLATFORM'))
);

COMMENT ON TABLE public.admin_user_role IS '用户与本地角色的多对多授权';

COMMENT ON COLUMN public.admin_user_role.user_id IS '被授予的账号';

COMMENT ON COLUMN public.admin_user_role.role_id IS '授予的角色';

COMMENT ON COLUMN public.admin_user_role.assigned_at IS '授予时间';

COMMENT ON COLUMN public.admin_user_role.source IS
    '授予来源：PLATFORM 由实例角色派生且本地不可移除，LOCAL 由本地分配';

-- ---------------------------------------------------------------------------
-- 平台生命周期命令的幂等记录。
-- ---------------------------------------------------------------------------

CREATE TABLE public.admin_lifecycle_operation (
    id uuid NOT NULL,
    idempotency_key character varying(200) NOT NULL,
    command character varying(32) NOT NULL,
    request_digest character(64) NOT NULL,
    state character varying(32) NOT NULL,
    platform_app_instance_id uuid,
    external_instance_id character varying(120),
    failure_code character varying(64),
    created_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    updated_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT admin_lifecycle_operation_pk PRIMARY KEY (id),
    CONSTRAINT admin_lifecycle_operation_idempotency_uk UNIQUE (idempotency_key),
    CONSTRAINT admin_lifecycle_operation_command_ck CHECK (
        command IN ('PROVISION', 'SUSPEND', 'RESUME', 'DEPROVISION')
    ),
    CONSTRAINT admin_lifecycle_operation_state_ck CHECK (
        state IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT admin_lifecycle_operation_digest_ck CHECK (request_digest ~ '^[0-9a-f]{64}$')
);

COMMENT ON TABLE public.admin_lifecycle_operation IS '平台生命周期命令的幂等记录；相同幂等键与相同请求摘要返回首次结果';

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
-- 审计：只追加终态动作，不记录 Token、Secret、口令与完整业务载荷。
-- ---------------------------------------------------------------------------

CREATE TABLE public.admin_audit_event (
    id uuid NOT NULL,
    occurred_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    actor_type character varying(32) NOT NULL,
    actor_id character varying(120),
    action character varying(64) NOT NULL,
    target_type character varying(64) NOT NULL,
    target_id character varying(120),
    workspace_id uuid,
    correlation_id character varying(120),
    outcome character varying(32) NOT NULL,
    CONSTRAINT admin_audit_event_pk PRIMARY KEY (id),
    CONSTRAINT admin_audit_event_actor_type_ck CHECK (
        actor_type IN ('USER', 'PLATFORM', 'SYSTEM')
    ),
    CONSTRAINT admin_audit_event_outcome_ck CHECK (
        outcome IN ('SUCCESS', 'FAILURE', 'DENIED')
    )
);

COMMENT ON TABLE public.admin_audit_event IS '非敏感审计事实，只追加终态动作';

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

CREATE INDEX admin_audit_event_occurred_at_ix
    ON public.admin_audit_event USING btree (occurred_at DESC);

CREATE INDEX admin_audit_event_actor_ix
    ON public.admin_audit_event USING btree (actor_type, actor_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- 示例业务域：公告。复制模板时替换为真实业务表，并保持 workspace 维度。
-- ---------------------------------------------------------------------------

CREATE TABLE public.admin_notice (
    id uuid NOT NULL,
    title character varying(200) NOT NULL,
    body text NOT NULL,
    status character varying(32) NOT NULL,
    workspace_id uuid,
    created_by uuid NOT NULL,
    published_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    updated_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    CONSTRAINT admin_notice_pk PRIMARY KEY (id),
    CONSTRAINT admin_notice_status_ck CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT admin_notice_title_ck CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    CONSTRAINT admin_notice_body_ck CHECK (length(body) <= 20000),
    CONSTRAINT admin_notice_version_ck CHECK (version >= 0),
    CONSTRAINT admin_notice_workspace_fk FOREIGN KEY (workspace_id)
        REFERENCES public.admin_workspace (id) ON DELETE SET NULL,
    CONSTRAINT admin_notice_created_by_fk FOREIGN KEY (created_by)
        REFERENCES public.admin_user (id)
);

COMMENT ON TABLE public.admin_notice IS '示例业务域：公告。展示业务表如何按 workspace 隔离并复用会话主体';

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

CREATE INDEX admin_notice_status_ix
    ON public.admin_notice USING btree (status, created_at DESC);
