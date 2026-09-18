-- Runlume 标准后台后端基线：服务端会话、本地身份、角色权限、平台工作区映射、生命周期操作、审计与示例业务域。

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
    CONSTRAINT admin_user_pk PRIMARY KEY (id),
    CONSTRAINT admin_user_email_ck CHECK (
        email = lower(email) AND email ~ '^[^[:space:]@]+@[^[:space:]@]+$'
    ),
    CONSTRAINT admin_user_status_ck CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT admin_user_version_ck CHECK (version >= 0),
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
    )
);

COMMENT ON TABLE public.admin_user IS '本地后台账号；LOCAL 为自有密码账号，PLATFORM 为平台 Launch 映射出的影子账号';

COMMENT ON COLUMN public.admin_user.email IS '规范化为小写的登录邮箱，同时作为用户唯一标识';

COMMENT ON COLUMN public.admin_user.password_hash IS 'BCrypt 口令摘要；平台映射账号不保存口令';

COMMENT ON COLUMN public.admin_user.credential_source IS '凭据来源：LOCAL 自有口令，PLATFORM 平台 Launch';

COMMENT ON COLUMN public.admin_user.status IS 'DISABLED 时拒绝登录并立即失效既有会话';

COMMENT ON COLUMN public.admin_user.version IS '并发更新使用的乐观锁版本';

-- 只有自有口令账号才要求邮箱唯一：平台映射账号的身份唯一性由
-- (platform_app_instance_id, platform_user_id) 保证，允许同名邮箱并存，避免
-- 平台 Launch 与本地账号因邮箱碰撞而互相接管。
CREATE UNIQUE INDEX admin_user_local_email_uk
    ON public.admin_user (email)
    WHERE credential_source = 'LOCAL';

CREATE UNIQUE INDEX admin_user_platform_uk
    ON public.admin_user (platform_app_instance_id, platform_user_id)
    WHERE platform_user_id IS NOT NULL;

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

COMMENT ON COLUMN public.admin_role.code IS '稳定角色码，业务规则和契约引用它而不是名称';

COMMENT ON COLUMN public.admin_role.built_in IS '内置角色不允许删除，避免运维误锁死后台';

CREATE TABLE public.admin_role_permission (
    role_id uuid NOT NULL,
    permission_code character varying(100) NOT NULL,
    CONSTRAINT admin_role_permission_pk PRIMARY KEY (role_id, permission_code),
    CONSTRAINT admin_role_permission_role_fk FOREIGN KEY (role_id)
        REFERENCES public.admin_role (id) ON DELETE CASCADE,
    CONSTRAINT admin_role_permission_code_ck CHECK (
        permission_code ~ '^(\*|[a-z][a-z0-9-]*)(:(\*|[a-z0-9-]+))*$'
    )
);

COMMENT ON TABLE public.admin_role_permission IS '角色授予的权限码；`*` 表示全部，`模块:*` 表示模块内全部';

CREATE TABLE public.admin_user_role (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    assigned_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    CONSTRAINT admin_user_role_pk PRIMARY KEY (user_id, role_id),
    CONSTRAINT admin_user_role_user_fk FOREIGN KEY (user_id)
        REFERENCES public.admin_user (id) ON DELETE CASCADE,
    CONSTRAINT admin_user_role_role_fk FOREIGN KEY (role_id)
        REFERENCES public.admin_role (id) ON DELETE CASCADE
);

COMMENT ON TABLE public.admin_user_role IS '用户与本地角色的多对多授权';

-- ---------------------------------------------------------------------------
-- 平台工作区映射：platform AppInstance 与本系统 workspace 的一对一映射。
-- ---------------------------------------------------------------------------

CREATE TABLE public.admin_workspace (
    id uuid NOT NULL,
    platform_account_id uuid NOT NULL,
    platform_app_instance_id uuid NOT NULL,
    module_key character varying(120) NOT NULL,
    module_version character varying(64) NOT NULL,
    external_instance_id character varying(120) NOT NULL,
    status character varying(32) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    updated_at timestamp with time zone DEFAULT transaction_timestamp() NOT NULL,
    suspended_at timestamp with time zone,
    deprovisioned_at timestamp with time zone,
    CONSTRAINT admin_workspace_pk PRIMARY KEY (id),
    CONSTRAINT admin_workspace_app_instance_uk UNIQUE (platform_app_instance_id),
    CONSTRAINT admin_workspace_external_uk UNIQUE (external_instance_id),
    CONSTRAINT admin_workspace_status_ck CHECK (
        status IN ('ACTIVE', 'SUSPENDED', 'DEPROVISIONING', 'DEPROVISIONED')
    ),
    CONSTRAINT admin_workspace_version_ck CHECK (version >= 0)
);

COMMENT ON TABLE public.admin_workspace IS '平台 AppInstance 与本系统 workspace 的一对一映射，是所有业务数据的租户隔离根';

COMMENT ON COLUMN public.admin_workspace.external_instance_id IS '返回给平台的不透明实例标识，平台不得解析其结构';

COMMENT ON COLUMN public.admin_workspace.status IS 'SUSPENDED 必须阻止新会话与每次业务写，而不只是隐藏入口';

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

CREATE INDEX admin_notice_status_ix
    ON public.admin_notice USING btree (status, created_at DESC);
