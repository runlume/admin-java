-- 平台成员的授权修订号：业务系统据此判断成员事实是否前移，避免用更旧的修订覆盖新状态。

ALTER TABLE public.admin_user
    ADD COLUMN membership_revision bigint DEFAULT 0 NOT NULL,
    ADD CONSTRAINT admin_user_membership_revision_ck CHECK (membership_revision >= 0);

COMMENT ON COLUMN public.admin_user.membership_revision IS
    '平台实例成员授权修订号；本地自有账号固定为 0，平台映射账号只允许单调递增';
