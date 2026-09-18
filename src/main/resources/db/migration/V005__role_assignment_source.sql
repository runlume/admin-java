-- 角色授予区分来源：平台派生（SAAS_ADMIN）与本地分配。平台派生只能由平台事实增删，
-- 本地管理员不能移除它，否则会与平台实例管理员身份不一致。

ALTER TABLE public.admin_user_role
    ADD COLUMN source character varying(16) DEFAULT 'LOCAL' NOT NULL,
    ADD CONSTRAINT admin_user_role_source_ck CHECK (source IN ('LOCAL', 'PLATFORM'));

COMMENT ON COLUMN public.admin_user_role.source IS
    '授予来源：PLATFORM 由实例角色派生且本地不可移除，LOCAL 由本地分配';
