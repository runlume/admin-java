-- 内置角色与权限码。权限码约定与 admin-design 前端一致：`*` 全部、`<命名空间>.<资源>.*` 资源内全部、
-- 其余精确匹配；命名空间取本部署在平台登记的模块标识（模板默认 example.admin），与平台 BUSINESS_RBAC
-- 的 `<module-namespace>.<resource>.<action>` 对齐。

INSERT INTO public.admin_role (id, code, name, description, built_in)
VALUES
    (
        '00000000-0000-4000-8000-000000000001',
        'admin',
        '系统管理员',
        '拥有全部权限，可管理用户、角色、公告与平台接入',
        true
    ),
    (
        '00000000-0000-4000-8000-000000000002',
        'member',
        '普通成员',
        '只能查看自己的资料和已发布公告',
        true
    );

INSERT INTO public.admin_role_permission (role_id, permission_code)
VALUES
    ('00000000-0000-4000-8000-000000000001', '*'),
    ('00000000-0000-4000-8000-000000000002', 'example.admin.profile.view'),
    ('00000000-0000-4000-8000-000000000002', 'example.admin.notice.view');
