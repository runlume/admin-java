-- 权限码改为点分制，与平台 BUSINESS_RBAC 的
-- <module-namespace>.<resource>.<action> 对齐。命名空间取本部署在平台登记的模块标识，
-- 模板默认 example.admin；复制模板时按实际模块标识整体替换本迁移中的前缀。

ALTER TABLE public.admin_role_permission
    DROP CONSTRAINT admin_role_permission_code_ck;

UPDATE public.admin_role_permission
SET permission_code = CASE permission_code
    WHEN 'profile:view' THEN 'example.admin.profile.view'
    WHEN 'user:view' THEN 'example.admin.member.view'
    WHEN 'user:create' THEN 'example.admin.member.create'
    WHEN 'user:update' THEN 'example.admin.member.update'
    WHEN 'user:disable' THEN 'example.admin.member.disable'
    WHEN 'user:*' THEN 'example.admin.member.*'
    WHEN 'role:view' THEN 'example.admin.role.view'
    WHEN 'audit:view' THEN 'example.admin.audit.view'
    WHEN 'notice:view' THEN 'example.admin.notice.view'
    WHEN 'notice:manage' THEN 'example.admin.notice.manage'
    WHEN 'notice:*' THEN 'example.admin.notice.*'
    WHEN 'platform:view' THEN 'example.admin.platform.view'
    ELSE permission_code
END;

ALTER TABLE public.admin_role_permission
    ADD CONSTRAINT admin_role_permission_code_ck CHECK (
        permission_code ~ '^(\*|[a-z0-9]+(\.[a-z0-9]+){2,}(\.\*)?)$'
    );

COMMENT ON COLUMN public.admin_role_permission.permission_code IS
    '权限码：`*` 表示全部，`<命名空间>.<资源>.*` 表示资源内全部，其余精确匹配';
