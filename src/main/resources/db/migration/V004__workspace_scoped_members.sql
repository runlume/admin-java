-- 平台影子账号绑定工作区：成员列表面向工作区，本地运营账号不进入任何工作区。
-- 平台账号的工作区必须与其平台实例映射一致，由复合外键保证。

-- 复合外键需要被引用列唯一；平台实例到工作区本来就是一比一。
ALTER TABLE public.admin_workspace
    ADD CONSTRAINT admin_workspace_id_app_instance_uk
        UNIQUE (id, platform_app_instance_id);

ALTER TABLE public.admin_user
    ADD COLUMN workspace_id uuid;

COMMENT ON COLUMN public.admin_user.workspace_id IS
    '平台账号所属本地工作区；本地运营账号为 NULL，且必须与其平台实例映射一致';

-- 回填既有平台账号；本地账号保持 NULL。
UPDATE public.admin_user member
SET workspace_id = workspace.id
FROM public.admin_workspace workspace
WHERE member.credential_source = 'PLATFORM'
  AND workspace.platform_app_instance_id = member.platform_app_instance_id;

ALTER TABLE public.admin_user
    ADD CONSTRAINT admin_user_platform_workspace_ck CHECK (
        (credential_source = 'PLATFORM' AND workspace_id IS NOT NULL)
        OR (credential_source = 'LOCAL' AND workspace_id IS NULL)
    );

ALTER TABLE public.admin_user
    ADD CONSTRAINT admin_user_workspace_instance_fk
        FOREIGN KEY (workspace_id, platform_app_instance_id)
        REFERENCES public.admin_workspace (id, platform_app_instance_id);

CREATE INDEX admin_user_workspace_ix
    ON public.admin_user USING btree (workspace_id);
