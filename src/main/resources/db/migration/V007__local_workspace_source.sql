-- 工作区从"平台映射的产物"泛化为"本系统自有的租户容器"：
--   PLATFORM 工作区来自平台开通，保留全部平台边界字段；
--   LOCAL    工作区由本系统自建（开源独立使用时由首个账号引导创建），平台字段为空。
-- 本地账号因此可以拥有自己的工作区并正常使用工作区隔离的业务数据。

ALTER TABLE public.admin_workspace
    ADD COLUMN source character varying(16) DEFAULT 'PLATFORM' NOT NULL;

ALTER TABLE public.admin_workspace
    ALTER COLUMN platform_account_id DROP NOT NULL,
    ALTER COLUMN platform_app_instance_id DROP NOT NULL,
    ALTER COLUMN module_key DROP NOT NULL,
    ALTER COLUMN module_version DROP NOT NULL,
    ALTER COLUMN external_instance_id DROP NOT NULL;

ALTER TABLE public.admin_workspace
    ADD CONSTRAINT admin_workspace_source_ck CHECK (
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
    );

COMMENT ON COLUMN public.admin_workspace.source IS
    '工作区来源：PLATFORM 由平台实例开通创建，LOCAL 由本系统自建';

-- 账号形状放宽为三态：平台会话账号、本地工作区账号、本地无工作区账号（自举/排障）。
ALTER TABLE public.admin_user
    DROP CONSTRAINT admin_user_platform_workspace_ck;

ALTER TABLE public.admin_user
    ADD CONSTRAINT admin_user_identity_shape_ck CHECK (
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
    );

-- 本地账号的工作区归属需要独立外键：复合外键在本地账号上因平台实例为空而不生效。
ALTER TABLE public.admin_user
    ADD CONSTRAINT admin_user_workspace_fk
        FOREIGN KEY (workspace_id) REFERENCES public.admin_workspace (id);
