-- ============================================================
-- SparkX 后台初始化脚本（PostgreSQL）
-- 数据库: sparkx  用户: sparkx
-- 在已连接 sparkx-postgres 容器后执行:
--   docker exec -i sparkx-postgres psql -U sparkx -d sparkx < sparkx.sql
-- ============================================================

-- pgvector 扩展：sample_query.embedding、kg_entity.embedding 等 vector 列依赖此扩展。
-- 镜像 crpi-.../sparkx/pgvector:15 已预装，首次执行需 CREATE EXTENSION 注册到当前库。
-- IF NOT EXISTS 保证幂等；sparkx 为 POSTGRES_USER 初始化的 superuser，有建扩展权限。
CREATE EXTENSION IF NOT EXISTS vector;

-- 清理（首次执行可忽略报错）
DROP TABLE IF EXISTS admin_user CASCADE;
DROP TABLE IF EXISTS admin_role CASCADE;
DROP TABLE IF EXISTS admin_menu CASCADE;
DROP TABLE IF EXISTS admin_department CASCADE;
DROP TABLE IF EXISTS drive_share CASCADE;
DROP TABLE IF EXISTS drive_grant CASCADE;
DROP TABLE IF EXISTS drive_file CASCADE;
DROP TABLE IF EXISTS drive_config CASCADE;
DROP TABLE IF EXISTS drive_favorite CASCADE;
DROP TABLE IF EXISTS drive_file_tag CASCADE;
DROP TABLE IF EXISTS drive_tag CASCADE;
-- 工单模块
DROP TABLE IF EXISTS wo_callback CASCADE;
DROP TABLE IF EXISTS wo_evaluate CASCADE;
DROP TABLE IF EXISTS wo_ticket_attachment CASCADE;
DROP TABLE IF EXISTS wo_ticket_log CASCADE;
DROP TABLE IF EXISTS wo_ticket CASCADE;
DROP TABLE IF EXISTS wo_quick_reply CASCADE;
DROP TABLE IF EXISTS wo_sla CASCADE;
DROP TABLE IF EXISTS wo_category CASCADE;
DROP TABLE IF EXISTS wo_contact CASCADE;
-- 知识库 / RAG 核心模块
DROP TABLE IF EXISTS knowledge_question CASCADE;
DROP TABLE IF EXISTS chunks CASCADE;
DROP TABLE IF EXISTS parent_chunks CASCADE;
DROP TABLE IF EXISTS document CASCADE;
DROP TABLE IF EXISTS knowledge_base CASCADE;
DROP TABLE IF EXISTS ai_model CASCADE;
-- 意图树 / 入库流水线 / 对话
DROP TABLE IF EXISTS t_intent_node CASCADE;
DROP TABLE IF EXISTS t_ingestion_task_node CASCADE;
DROP TABLE IF EXISTS t_ingestion_pipeline_node CASCADE;
DROP TABLE IF EXISTS t_conversation_summary CASCADE;
DROP TABLE IF EXISTS t_conversation_message CASCADE;

-- ------------------------------------------------------------
-- 1. 角色表
-- ------------------------------------------------------------
CREATE TABLE admin_role (
    id           SERIAL PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL DEFAULT '' ,
    menu         TEXT,
    status       SMALLINT     NOT NULL DEFAULT 1,
    create_time  TIMESTAMP,
    update_time  TIMESTAMP
);
COMMENT ON TABLE  admin_role IS '角色表';
COMMENT ON COLUMN admin_role.menu IS '角色拥有的菜单节点 id（逗号分隔），超管为 *';

-- ------------------------------------------------------------
-- 2. 管理员表
-- ------------------------------------------------------------
CREATE TABLE admin_user (
    id               SERIAL PRIMARY KEY,
    account          VARCHAR(30)  NOT NULL DEFAULT '',
    nickname         VARCHAR(50)  NOT NULL DEFAULT '',
    password         VARCHAR(64)  NOT NULL DEFAULT '',
    salt             VARCHAR(64)  NOT NULL DEFAULT '',
    avatar           VARCHAR(255) NOT NULL DEFAULT '',
    role_id          INTEGER,
    dept_id          INTEGER,
    status           SMALLINT     NOT NULL DEFAULT 1,
    last_login_ip    VARCHAR(55)  NOT NULL DEFAULT '',
    last_login_time  TIMESTAMP,
    create_time      TIMESTAMP,
    update_time      TIMESTAMP
);
COMMENT ON TABLE admin_user IS '员工表';
COMMENT ON COLUMN admin_user.salt IS '加密盐（每用户独立）';
COMMENT ON COLUMN admin_user.dept_id IS '所属部门 id';
CREATE UNIQUE INDEX uk_admin_user_account ON admin_user (account);

-- ------------------------------------------------------------
-- 3. 菜单表
-- ------------------------------------------------------------
CREATE TABLE admin_menu (
    id           SERIAL PRIMARY KEY,
    pid          INTEGER      NOT NULL DEFAULT 0,
    name         VARCHAR(100) NOT NULL DEFAULT '',
    type         SMALLINT     NOT NULL DEFAULT 1,
    flag         VARCHAR(155) NOT NULL DEFAULT '',
    path         VARCHAR(155) NOT NULL DEFAULT '',
    component    VARCHAR(255) NOT NULL DEFAULT '',
    auth         VARCHAR(255) NOT NULL DEFAULT '',
    icon         VARCHAR(155) NOT NULL DEFAULT '',
    sort         INTEGER      NOT NULL DEFAULT 0,
    hidden       SMALLINT     NOT NULL DEFAULT 0,          -- 是否在侧边栏隐藏 0:显示 1:隐藏（仍注册路由，可URL直访）
    status       SMALLINT     NOT NULL DEFAULT 1,
    create_time  TIMESTAMP,
    update_time  TIMESTAMP
);
COMMENT ON TABLE  admin_menu IS '菜单/权限节点表';
COMMENT ON COLUMN admin_menu.pid     IS '父级 id，0 为根';
COMMENT ON COLUMN admin_menu.type    IS '类型 1:菜单 2:功能按钮';
COMMENT ON COLUMN admin_menu.flag    IS '路由标识（拼接前端路由 name 用）';
COMMENT ON COLUMN admin_menu.auth    IS '权限标识（接口鉴权用，如 admin/add）';
COMMENT ON COLUMN admin_menu.hidden  IS '是否在侧边栏隐藏 0:显示 1:隐藏（仍注册路由，可URL直访，如知识库详情页）';

-- ------------------------------------------------------------
-- 4. 部门表（树形：pid 父级，0 为根）
-- ------------------------------------------------------------
CREATE TABLE admin_department (
    id           SERIAL PRIMARY KEY,
    pid          INTEGER      NOT NULL DEFAULT 0,
    name         VARCHAR(50)  NOT NULL DEFAULT '',
    leader_id    INTEGER,
    phone        VARCHAR(20)  NOT NULL DEFAULT '',
    sort         INTEGER      NOT NULL DEFAULT 0,
    status       SMALLINT     NOT NULL DEFAULT 1,
    create_time  TIMESTAMP,
    update_time  TIMESTAMP
);
COMMENT ON TABLE  admin_department IS '部门表';
COMMENT ON COLUMN admin_department.pid       IS '父级 id，0 为根';
COMMENT ON COLUMN admin_department.leader_id IS '部门主管（admin_user.id）';
COMMENT ON COLUMN admin_department.sort      IS '排序（越大越靠前）';

-- ------------------------------------------------------------
-- 5. 云盘文件表（文件 + 目录统一，树形：pid 父级，0 为根）
-- ------------------------------------------------------------
CREATE TABLE drive_file (
    id           SERIAL PRIMARY KEY,
    pid          INTEGER      NOT NULL DEFAULT 0,
    name         VARCHAR(255) NOT NULL DEFAULT '',
    is_dir       SMALLINT     NOT NULL DEFAULT 0,
    size         BIGINT       NOT NULL DEFAULT 0,
    ext          VARCHAR(30)  NOT NULL DEFAULT '',
    object_name  VARCHAR(500) NOT NULL DEFAULT '',
    mime         VARCHAR(100) NOT NULL DEFAULT '',
    owner_id     INTEGER,
    dept_id      INTEGER,
    status       SMALLINT     NOT NULL DEFAULT 1,
    delete_time  TIMESTAMP,
    delete_by    INTEGER,
    is_favorite  SMALLINT     NOT NULL DEFAULT 0,
    create_time  TIMESTAMP,
    update_time  TIMESTAMP
);
COMMENT ON TABLE  drive_file IS '企业云盘文件/目录表';
COMMENT ON COLUMN drive_file.pid         IS '父级 id，0 为根';
COMMENT ON COLUMN drive_file.is_dir      IS '1:目录 0:文件';
COMMENT ON COLUMN drive_file.object_name IS 'MinIO 对象名（文件实际存储路径）';
COMMENT ON COLUMN drive_file.owner_id    IS '创建人（admin_user.id）';
COMMENT ON COLUMN drive_file.dept_id     IS '创建人部门（admin_department.id，冗余便于授权）';
COMMENT ON COLUMN drive_file.status      IS '1:正常 0:已删除';
COMMENT ON COLUMN drive_file.delete_time IS '删除时间（进回收站的时间，null 表示未删除）';
COMMENT ON COLUMN drive_file.delete_by   IS '删除人 id（admin_user.id）';
COMMENT ON COLUMN drive_file.is_favorite IS '是否当前用户收藏（冗余，1:是 0:否）';

-- ------------------------------------------------------------
-- 6. 云盘授权表（目录级：按部门或按人授权）
-- ------------------------------------------------------------
CREATE TABLE drive_grant (
    id           SERIAL PRIMARY KEY,
    file_id      INTEGER      NOT NULL,
    grant_type   SMALLINT     NOT NULL DEFAULT 1,
    target_id    INTEGER      NOT NULL,
    create_time  TIMESTAMP
);
COMMENT ON TABLE  drive_grant IS '云盘目录授权表';
COMMENT ON COLUMN drive_grant.file_id    IS '目录 id（drive_file.id）';
COMMENT ON COLUMN drive_grant.grant_type IS '1:按部门 2:按人';
COMMENT ON COLUMN drive_grant.target_id  IS '授权目标 id（部门id 或 用户id）';

-- ------------------------------------------------------------
-- 7. 云盘分享表
-- ------------------------------------------------------------
CREATE TABLE drive_share (
    id           SERIAL PRIMARY KEY,
    file_id      INTEGER      NOT NULL,
    share_code   VARCHAR(32)  NOT NULL DEFAULT '',
    password     VARCHAR(64)  NOT NULL DEFAULT '',
    expire_time  TIMESTAMP,
    view_count   INTEGER      NOT NULL DEFAULT 0,
    owner_id     INTEGER,
    create_time  TIMESTAMP
);
COMMENT ON TABLE  drive_share IS '云盘分享表';
COMMENT ON COLUMN drive_share.file_id     IS '文件/目录 id（drive_file.id）';
COMMENT ON COLUMN drive_share.share_code  IS '分享提取码（短链码）';
COMMENT ON COLUMN drive_share.password    IS '分享密码（MD5 哈希，空表示无密码）';
COMMENT ON COLUMN drive_share.expire_time IS '过期时间（null 永久）';
CREATE UNIQUE INDEX uk_drive_share_code ON drive_share (share_code);

-- ------------------------------------------------------------
-- 8. 云盘上传配置表（单行，id 固定为 1）
-- ------------------------------------------------------------
CREATE TABLE drive_config (
    id              SERIAL PRIMARY KEY,
    allow_exts      TEXT,
    block_exts      TEXT,
    max_size        INTEGER      NOT NULL DEFAULT 500,
    allow_share     SMALLINT     NOT NULL DEFAULT 1,
    allow_download  SMALLINT     NOT NULL DEFAULT 1,
    update_time     TIMESTAMP
);
COMMENT ON TABLE  drive_config IS '云盘配置表';
COMMENT ON COLUMN drive_config.allow_exts     IS '允许的扩展名（逗号分隔，空表示不限制）';
COMMENT ON COLUMN drive_config.block_exts     IS '禁止的扩展名（逗号分隔）';
COMMENT ON COLUMN drive_config.max_size       IS '单文件最大大小（MB）';
COMMENT ON COLUMN drive_config.allow_share    IS '是否允许分享 1:允许 0:禁止';
COMMENT ON COLUMN drive_config.allow_download IS '是否允许下载 1:允许 0:禁止';

-- ------------------------------------------------------------
-- 8.1 云盘收藏表
-- ------------------------------------------------------------
CREATE TABLE drive_favorite (
    id          SERIAL PRIMARY KEY,
    file_id     INTEGER NOT NULL,
    user_id     INTEGER NOT NULL,
    create_time TIMESTAMP
);
COMMENT ON TABLE  drive_favorite IS '云盘收藏表';
COMMENT ON COLUMN drive_favorite.file_id IS '文件 id（drive_file.id）';
COMMENT ON COLUMN drive_favorite.user_id IS '收藏人 id（admin_user.id）';
CREATE UNIQUE INDEX uk_drive_favorite ON drive_favorite (file_id, user_id);

-- ------------------------------------------------------------
-- 8.2 云盘标签表（每用户独立维护）
-- ------------------------------------------------------------
CREATE TABLE drive_tag (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL DEFAULT '',
    color       VARCHAR(20)  NOT NULL DEFAULT '',
    owner_id    INTEGER      NOT NULL,
    create_time TIMESTAMP
);
COMMENT ON TABLE  drive_tag IS '云盘标签表';
COMMENT ON COLUMN drive_tag.name     IS '标签名称';
COMMENT ON COLUMN drive_tag.color    IS '标签颜色（前端展示）';
COMMENT ON COLUMN drive_tag.owner_id IS '创建人 id（admin_user.id）';

-- ------------------------------------------------------------
-- 8.3 云盘文件-标签关联表（多对多）
-- ------------------------------------------------------------
CREATE TABLE drive_file_tag (
    id      SERIAL PRIMARY KEY,
    file_id INTEGER NOT NULL,
    tag_id  INTEGER NOT NULL
);
COMMENT ON TABLE drive_file_tag IS '云盘文件-标签关联表';
CREATE UNIQUE INDEX uk_drive_file_tag ON drive_file_tag (file_id, tag_id);

-- ============================================================
-- 种子数据
-- ============================================================

-- ------------------------------------------------------------
-- 角色
--   menu: 角色拥有的菜单+按钮节点 id（逗号分隔），超管为 *
-- ------------------------------------------------------------
INSERT INTO admin_role (id, name, menu, status, create_time, update_time) VALUES
    (1, '超级管理员', '*', 1, now(), now()),
    (2, '运营', '1,2,3,4,6,7,8', 1, now(), now());

-- ------------------------------------------------------------
-- 管理员
--   账号: admin   密码: 123456
--   算法: md5(md5(password + salt) + salt)
--   salt: xservice-admin-salt-2026
--   注: 后端启动时会自动校验并修正该哈希，无需担心哈希值不匹配。
-- ------------------------------------------------------------
INSERT INTO admin_user (id, account, nickname, password, salt, avatar, role_id, dept_id, status, create_time, update_time) VALUES
    (1, 'admin', '管理员', '9b94e5b87d65a9590aed658a6624d665', 'xservice-admin-salt-2026', '', 1, 1, 1, now(), now()),
    (2, 'test',  '测试员', '9b94e5b87d65a9590aed658a6624d665', 'xservice-admin-salt-2026', '', 2, 2, 1, now(), now());

-- ------------------------------------------------------------
-- 部门（示例：总公司 -> 技术部 / 市场部）
-- ------------------------------------------------------------
INSERT INTO admin_department (id, pid, name, leader_id, phone, sort, status, create_time, update_time) VALUES
    (1, 0, '总公司',   1,    '010-88888888', 100, 1, now(), now()),
    (2, 1, '技术部',   2,    '010-66666666', 90,  1, now(), now()),
    (3, 1, '市场部',   NULL, '010-77777777', 80,  1, now(), now());

-- ------------------------------------------------------------
-- 菜单
--   component 值对应 admin/src/views 下的文件，父级用 LAYOUT
--   name 计算: 根=flag, 子=flag_path（由后端 MenuTreeUtils 完成）
--   注意: 同一父级下子菜单的 path 必须互不相同，否则 vue-router 路径冲突
-- ------------------------------------------------------------
INSERT INTO admin_menu (id, pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    -- 一级目录
    (1, 0, '主页面版', 1, 'dashboard',  '#', 'LAYOUT', '#', 'DashboardOutlined',         99, 1, now(), now()),
    (3, 0, '部门员工', 1, 'permission', '#', 'LAYOUT', '#', 'SafetyCertificateOutlined', 80, 1, now(), now()),
    (20, 0, '企业云盘', 1, 'drive',     '#', 'LAYOUT', '#', 'CloudUploadOutlined',       70, 1, now(), now()),

    -- 二级菜单
    (2, 1, '主控台',   1, 'dashboard', 'console', '/dashboard/index', 'dashboard/index', '', 90, 1, now(), now()),
    (4, 3, '员工管理', 1, 'admin',     'admin',   '/admin/index',     'admin/index',     '', 70, 1, now(), now()),
    (5, 3, '角色管理', 1, 'role',      'role',    '/role/index',      'role/index',      '', 60, 1, now(), now()),
    (12, 3, '菜单管理', 1, 'menu',     'menu',    '/menu/index',      'menu/index',      '', 50, 1, now(), now()),

    -- 员工管理 功能按钮(type=2) —— auth 对应后端接口 /admin/xxx
    (6, 4, '添加', 2, '', '', '', 'admin/add',  '', 70, 1, now(), now()),
    (7, 4, '编辑', 2, '', '', '', 'admin/edit', '', 60, 1, now(), now()),
    (8, 4, '删除', 2, '', '', '', 'admin/del',  '', 50, 1, now(), now()),

    -- 角色管理 功能按钮(type=2) —— auth 对应后端接口 /role/xxx
    (9,  5, '添加', 2, '', '', '', 'role/add',  '', 70, 1, now(), now()),
    (10, 5, '编辑', 2, '', '', '', 'role/edit', '', 60, 1, now(), now()),
    (11, 5, '删除', 2, '', '', '', 'role/del',  '', 50, 1, now(), now()),

    -- 菜单管理 功能按钮(type=2) —— auth 对应后端接口 /menu/xxx
    (13, 12, '添加', 2, '', '', '', 'menu/add',  '', 70, 1, now(), now()),
    (14, 12, '编辑', 2, '', '', '', 'menu/edit', '', 60, 1, now(), now()),
    (15, 12, '删除', 2, '', '', '', 'menu/del',  '', 50, 1, now(), now()),

    -- 部门管理 二级菜单 + 功能按钮(type=2) —— auth 对应后端接口 /dept/xxx
    (16, 3,  '部门管理', 1, 'dept', 'dept', '/dept/index', 'dept/index', 'ApartmentOutlined', 65, 1, now(), now()),
    (17, 16, '添加', 2, '', '', '', 'dept/add',  '', 70, 1, now(), now()),
    (18, 16, '编辑', 2, '', '', '', 'dept/edit', '', 60, 1, now(), now()),
    (19, 16, '删除', 2, '', '', '', 'dept/del',  '', 50, 1, now(), now()),

    -- 企业云盘 二级菜单 + 功能按钮(type=2) —— auth 对应后端接口 /drive/xxx
    (27, 20, '我的云盘', 1, 'drive', 'drive', '/drive/index', 'drive/index', '', 90, 1, now(), now()),
    (21, 27, '上传文件', 2, '', '', '', 'drive/upload',   '', 80, 1, now(), now()),
    (22, 27, '下载',     2, '', '', '', 'drive/download', '', 70, 1, now(), now()),
    (23, 27, '删除',     2, '', '', '', 'drive/del',      '', 60, 1, now(), now()),
    (24, 27, '新建目录', 2, '', '', '', 'drive/mkdir',    '', 50, 1, now(), now()),
    (25, 27, '授权',     2, '', '', '', 'drive/grant',    '', 40, 1, now(), now()),
    (26, 27, '分享',     2, '', '', '', 'drive/share',    '', 30, 1, now(), now()),

    -- 云盘上传配置 子菜单
    (28, 20, '上传配置', 1, 'config', 'config', '/drive/config', 'driveConfig/index', 'SettingOutlined', 80, 1, now(), now()),

    -- 云盘升级功能按钮(type=2) —— 垃圾箱/收藏/标签/复制/剪切
    (29, 27, '复制',       2, '', '', '', 'drive/copy',       '', 25, 1, now(), now()),
    (30, 27, '剪切',       2, '', '', '', 'drive/moveBatch',  '', 24, 1, now(), now()),
    (31, 27, '收藏',       2, '', '', '', 'drive/favorite',   '', 23, 1, now(), now()),
    (32, 27, '我的收藏',   2, '', '', '', 'drive/favorites',  '', 22, 1, now(), now()),
    (33, 27, '标签列表',   2, '', '', '', 'drive/tags',       '', 21, 1, now(), now()),
    (34, 27, '新建标签',   2, '', '', '', 'drive/tag/create', '', 20, 1, now(), now()),
    (35, 27, '删除标签',   2, '', '', '', 'drive/tag/delete', '', 19, 1, now(), now()),
    (36, 27, '文件打标签', 2, '', '', '', 'drive/fileTags',   '', 18, 1, now(), now()),
    (37, 27, '按标签筛选', 2, '', '', '', 'drive/listByTag',  '', 17, 1, now(), now()),
    (38, 27, '重命名',     2, '', '', '', 'drive/rename',     '', 16, 1, now(), now()),
    (39, 27, '移动',       2, '', '', '', 'drive/move',       '', 15, 1, now(), now()),
    (40, 27, '预览',       2, '', '', '', 'drive/preview',    '', 14, 1, now(), now()),
    (41, 27, '回收站列表', 2, '', '', '', 'drive/trash',      '', 13, 1, now(), now()),
    (42, 27, '恢复',       2, '', '', '', 'drive/restore',    '', 12, 1, now(), now()),
    (43, 27, '彻底删除',   2, '', '', '', 'drive/destroy',    '', 11, 1, now(), now());

-- ------------------------------------------------------------
-- 云盘上传配置（默认：代码类文件禁止）
-- ------------------------------------------------------------
INSERT INTO drive_config (id, allow_exts, block_exts, max_size, allow_share, allow_download, update_time)
VALUES (1, '',
    'js,ts,jsx,tsx,mjs,cjs,vue,svelte,java,kt,scala,groovy,gradle,py,pyc,go,rs,c,h,cpp,cc,cxx,hpp,hxx,cs,rb,php,swift,m,mm,sh,bash,zsh,fish,bat,cmd,ps1,sql,pl,lua,r,dart,html,htm,css,scss,sass,less,json,xml,yaml,yml,toml,ini,conf,env',
    500, 1, 1, now());

-- ============================================================
-- 工单模块（workOrder）
-- ============================================================

-- ------------------------------------------------------------
-- 1. 联系人表（客户，无账号，客服代录）
-- ------------------------------------------------------------
CREATE TABLE wo_contact (
    id           SERIAL PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL DEFAULT '',
    phone        VARCHAR(20)  NOT NULL DEFAULT '',
    email        VARCHAR(100) NOT NULL DEFAULT '',
    company      VARCHAR(100) NOT NULL DEFAULT '',
    address      VARCHAR(255) NOT NULL DEFAULT '',
    area_id      INTEGER,
    remark       VARCHAR(500),
    creator_id   INTEGER,
    create_time  TIMESTAMP,
    update_time  TIMESTAMP
);
COMMENT ON TABLE  wo_contact IS '工单联系人表';
COMMENT ON COLUMN wo_contact.area_id IS '地区 id（预留）';
COMMENT ON COLUMN wo_contact.creator_id IS '创建人（admin_user.id）';

-- ------------------------------------------------------------
-- 2. 工单分类表（多级树，pid 自引用）
-- ------------------------------------------------------------
CREATE TABLE wo_category (
    id           SERIAL PRIMARY KEY,
    pid          INTEGER      NOT NULL DEFAULT 0,
    name         VARCHAR(100) NOT NULL DEFAULT '',
    sort         INTEGER      NOT NULL DEFAULT 0,
    status       SMALLINT     NOT NULL DEFAULT 1,
    create_time  TIMESTAMP,
    update_time  TIMESTAMP
);
COMMENT ON TABLE wo_category IS '工单分类表';
COMMENT ON COLUMN wo_category.pid IS '父级 id，0 为根';
COMMENT ON COLUMN wo_category.status IS '1:启用 2:禁用';

-- ------------------------------------------------------------
-- 3. SLA 策略表
-- ------------------------------------------------------------
CREATE TABLE wo_sla (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL DEFAULT '',
    priority        SMALLINT     NOT NULL DEFAULT 3,
    response_hours  INTEGER      NOT NULL DEFAULT 1,
    resolve_hours   INTEGER      NOT NULL DEFAULT 24,
    status          SMALLINT     NOT NULL DEFAULT 1,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP
);
COMMENT ON TABLE  wo_sla IS 'SLA 策略表';
COMMENT ON COLUMN wo_sla.priority IS '匹配的优先级: 1紧急 2高 3中 4低';
COMMENT ON COLUMN wo_sla.response_hours IS '首次响应时限（小时）';
COMMENT ON COLUMN wo_sla.resolve_hours IS '解决时限（小时）';
COMMENT ON COLUMN wo_sla.status IS '1:启用 2:禁用';

-- ------------------------------------------------------------
-- 4. 快捷回复话术表
-- ------------------------------------------------------------
CREATE TABLE wo_quick_reply (
    id           SERIAL PRIMARY KEY,
    title        VARCHAR(100) NOT NULL DEFAULT '',
    content      TEXT,
    category_id  INTEGER,
    sort         INTEGER      NOT NULL DEFAULT 0,
    status       SMALLINT     NOT NULL DEFAULT 1,
    create_time  TIMESTAMP,
    update_time  TIMESTAMP
);
COMMENT ON TABLE wo_quick_reply IS '快捷回复话术表';
COMMENT ON COLUMN wo_quick_reply.status IS '1:启用 2:禁用';

-- ------------------------------------------------------------
-- 5. 工单主表（核心）
-- ------------------------------------------------------------
CREATE TABLE wo_ticket (
    id                     SERIAL PRIMARY KEY,
    ticket_no              VARCHAR(30)  NOT NULL DEFAULT '',
    title                  VARCHAR(200) NOT NULL DEFAULT '',
    description            TEXT,
    contact_id             INTEGER,
    category_id            INTEGER,
    ticket_type            SMALLINT     NOT NULL DEFAULT 1,
    priority               SMALLINT     NOT NULL DEFAULT 3,
    source                 SMALLINT     NOT NULL DEFAULT 5,
    status                 SMALLINT     NOT NULL DEFAULT 1,
    creator_id             INTEGER,
    assignee_id            INTEGER,
    dept_id                INTEGER,
    from_conversation_id   INTEGER,
    sla_id                 INTEGER,
    sla_response_deadline  TIMESTAMP,
    sla_resolve_deadline   TIMESTAMP,
    first_response_time    TIMESTAMP,
    resolved_time          TIMESTAMP,
    closed_time            TIMESTAMP,
    evaluate_status        SMALLINT     NOT NULL DEFAULT 0,
    stars                  SMALLINT,
    create_time            TIMESTAMP,
    update_time            TIMESTAMP
);
COMMENT ON TABLE  wo_ticket IS '工单主表';
COMMENT ON COLUMN wo_ticket.ticket_no IS '工单号 TK-yyyyMMdd-xxxx';
COMMENT ON COLUMN wo_ticket.ticket_type IS '类型: 1咨询 2投诉 3建议 4报障 5售后';
COMMENT ON COLUMN wo_ticket.priority IS '优先级: 1紧急 2高 3中 4低';
COMMENT ON COLUMN wo_ticket.source IS '来源: 1电话 2IM会话 3邮件 4公众号 5后台创建';
COMMENT ON COLUMN wo_ticket.status IS '状态: 1待接待 2处理中 3待回复 4已解决 5已关闭 6已取消 7已转接';
COMMENT ON COLUMN wo_ticket.creator_id IS '创建客服（admin_user.id）';
COMMENT ON COLUMN wo_ticket.assignee_id IS '当前受理人（admin_user.id）';
COMMENT ON COLUMN wo_ticket.dept_id IS '所属部门（admin_department.id）';
COMMENT ON COLUMN wo_ticket.from_conversation_id IS '来源会话 id（IM 转工单时带入，可空）';
COMMENT ON COLUMN wo_ticket.evaluate_status IS '评价状态: 0待评 1已评 2不参与';
COMMENT ON COLUMN wo_ticket.stars IS '满意度星级 1-5';
CREATE UNIQUE INDEX uk_wo_ticket_no ON wo_ticket (ticket_no);
CREATE INDEX idx_wo_ticket_assignee ON wo_ticket (assignee_id);
CREATE INDEX idx_wo_ticket_status ON wo_ticket (status);
CREATE INDEX idx_wo_ticket_contact ON wo_ticket (contact_id);

-- ------------------------------------------------------------
-- 6. 工单流水表（统一记录：回复/内部备注/转接/改状态/升级/分配/认领/系统）
-- ------------------------------------------------------------
CREATE TABLE wo_ticket_log (
    id             SERIAL PRIMARY KEY,
    ticket_id      INTEGER      NOT NULL,
    log_type       SMALLINT     NOT NULL DEFAULT 1,
    content        TEXT,
    from_admin_id  INTEGER,
    to_admin_id    INTEGER,
    from_dept_id   INTEGER,
    to_dept_id     INTEGER,
    before_status  SMALLINT,
    after_status   SMALLINT,
    is_internal    SMALLINT     NOT NULL DEFAULT 0,
    create_time    TIMESTAMP
);
COMMENT ON TABLE  wo_ticket_log IS '工单流水表';
COMMENT ON COLUMN wo_ticket_log.log_type IS '1对外回复 2内部备注 3转接 4改状态 5升级 6分配 7认领 8系统';
COMMENT ON COLUMN wo_ticket_log.is_internal IS '是否仅内部可见: 0否 1是';
CREATE INDEX idx_wo_ticket_log_ticket ON wo_ticket_log (ticket_id);

-- ------------------------------------------------------------
-- 7. 工单附件表
-- ------------------------------------------------------------
CREATE TABLE wo_ticket_attachment (
    id           SERIAL PRIMARY KEY,
    ticket_id    INTEGER      NOT NULL,
    log_id       INTEGER,
    file_name    VARCHAR(255) NOT NULL DEFAULT '',
    file_url     VARCHAR(500) NOT NULL DEFAULT '',
    file_size    BIGINT       NOT NULL DEFAULT 0,
    file_type    VARCHAR(50)  NOT NULL DEFAULT '',
    uploader_id  INTEGER,
    create_time  TIMESTAMP
);
COMMENT ON TABLE  wo_ticket_attachment IS '工单附件表';
COMMENT ON COLUMN wo_ticket_attachment.log_id IS '关联流水 id（可空，挂在具体回复上）';
COMMENT ON COLUMN wo_ticket_attachment.uploader_id IS '上传人（admin_user.id）';
CREATE INDEX idx_wo_ticket_attachment_ticket ON wo_ticket_attachment (ticket_id);

-- ------------------------------------------------------------
-- 8. 工单评价表
-- ------------------------------------------------------------
CREATE TABLE wo_evaluate (
    id           SERIAL PRIMARY KEY,
    ticket_id    INTEGER      NOT NULL,
    stars        SMALLINT     NOT NULL DEFAULT 5,
    tags         VARCHAR(255),
    content      VARCHAR(500),
    evaluator_id INTEGER,
    admin_id     INTEGER,
    create_time  TIMESTAMP
);
COMMENT ON TABLE  wo_evaluate IS '工单评价表';
COMMENT ON COLUMN wo_evaluate.stars IS '满意度星级 1-5';
COMMENT ON COLUMN wo_evaluate.tags IS '评价标签（逗号分隔）';
COMMENT ON COLUMN wo_evaluate.admin_id IS '被评客服（admin_user.id）';
CREATE INDEX idx_wo_evaluate_ticket ON wo_evaluate (ticket_id);

-- ------------------------------------------------------------
-- 9. 工单回访表
-- ------------------------------------------------------------
CREATE TABLE wo_callback (
    id            SERIAL PRIMARY KEY,
    ticket_id     INTEGER      NOT NULL,
    callback_type SMALLINT     NOT NULL DEFAULT 1,
    admin_id      INTEGER,
    result        VARCHAR(255),
    content       VARCHAR(500),
    next_time     TIMESTAMP,
    create_time   TIMESTAMP
);
COMMENT ON TABLE  wo_callback IS '工单回访表';
COMMENT ON COLUMN wo_callback.callback_type IS '1满意度回访 2常规回访';
COMMENT ON COLUMN wo_callback.admin_id IS '回访人（admin_user.id）';
CREATE INDEX idx_wo_callback_ticket ON wo_callback (ticket_id);

-- ------------------------------------------------------------
-- 工单模块默认数据：SLA 策略（按优先级）
-- ------------------------------------------------------------
INSERT INTO wo_sla (id, name, priority, response_hours, resolve_hours, status, create_time, update_time) VALUES
    (1, '紧急工单SLA', 1, 0,   2,   1, now(), now()),
    (2, '高优工单SLA', 2, 1,   8,   1, now(), now()),
    (3, '常规工单SLA', 3, 4,   48,  1, now(), now()),
    (4, '低优工单SLA', 4, 24,  168, 1, now(), now());

-- ------------------------------------------------------------
-- 工单模块默认数据：一级分类示例
-- ------------------------------------------------------------
INSERT INTO wo_category (id, pid, name, sort, status, create_time, update_time) VALUES
    (1, 0, '售前咨询', 90, 1, now(), now()),
    (2, 0, '售后服务', 80, 1, now(), now()),
    (3, 0, '投诉建议', 70, 1, now(), now()),
    (4, 0, '技术支持', 60, 1, now(), now());

-- 工单模块菜单已移至文件末尾（自增 id，须在所有硬编码 id 入库后执行，见末尾「工单模块菜单」段）

-- 重置序列，避免后续插入主键冲突
SELECT setval('admin_role_id_seq', (SELECT MAX(id) FROM admin_role));
SELECT setval('admin_user_id_seq', (SELECT MAX(id) FROM admin_user));
SELECT setval('admin_menu_id_seq', (SELECT MAX(id) FROM admin_menu));
SELECT setval('admin_department_id_seq', (SELECT MAX(id) FROM admin_department));
SELECT setval('drive_file_id_seq',  (SELECT MAX(id) FROM drive_file));
SELECT setval('drive_grant_id_seq', (SELECT MAX(id) FROM drive_grant));
SELECT setval('drive_share_id_seq', (SELECT MAX(id) FROM drive_share));
SELECT setval('drive_favorite_id_seq', (SELECT MAX(id) FROM drive_favorite));
SELECT setval('drive_tag_id_seq',      (SELECT MAX(id) FROM drive_tag));
SELECT setval('drive_file_tag_id_seq', (SELECT MAX(id) FROM drive_file_tag));
-- 工单模块序列重置
SELECT setval('wo_contact_id_seq',            (SELECT MAX(id) FROM wo_contact));
SELECT setval('wo_category_id_seq',           (SELECT MAX(id) FROM wo_category));
SELECT setval('wo_sla_id_seq',                (SELECT MAX(id) FROM wo_sla));
SELECT setval('wo_quick_reply_id_seq',        (SELECT MAX(id) FROM wo_quick_reply));
SELECT setval('wo_ticket_id_seq',             (SELECT MAX(id) FROM wo_ticket));
SELECT setval('wo_ticket_log_id_seq',         (SELECT MAX(id) FROM wo_ticket_log));
SELECT setval('wo_ticket_attachment_id_seq',  (SELECT MAX(id) FROM wo_ticket_attachment));
SELECT setval('wo_evaluate_id_seq',           (SELECT MAX(id) FROM wo_evaluate));
SELECT setval('wo_callback_id_seq',           (SELECT MAX(id) FROM wo_callback));

-- ============================================================
-- 在线客服模块（im）
-- ============================================================

-- ------------------------------------------------------------
-- 0. 安全清理（重建时避免残留）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS im_evaluate CASCADE;
DROP TABLE IF EXISTS im_quick_reply CASCADE;
DROP TABLE IF EXISTS im_group_member CASCADE;
DROP TABLE IF EXISTS im_group CASCADE;
DROP TABLE IF EXISTS im_customer_service CASCADE;
DROP TABLE IF EXISTS im_message CASCADE;
DROP TABLE IF EXISTS im_conversation CASCADE;
DROP TABLE IF EXISTS im_visitor CASCADE;
DROP TABLE IF EXISTS im_stats_daily CASCADE;
DROP TABLE IF EXISTS im_config CASCADE;
DROP TABLE IF EXISTS im_embed_channels CASCADE;

-- ------------------------------------------------------------
-- 1. 访客表（C 端无账号，通过 visitor_key 长期识别）
-- ------------------------------------------------------------
CREATE TABLE im_visitor (
    id               SERIAL PRIMARY KEY,
    nick             VARCHAR(50)  NOT NULL DEFAULT '',
    avatar           VARCHAR(255) NOT NULL DEFAULT '',
    visitor_key      VARCHAR(64)  NOT NULL DEFAULT '',
    ip               VARCHAR(64)  NOT NULL DEFAULT '',
    ua               VARCHAR(500) NOT NULL DEFAULT '',
    source_url       VARCHAR(500) NOT NULL DEFAULT '',
    last_active_time TIMESTAMP,
    create_time      TIMESTAMP
);
COMMENT ON TABLE  im_visitor IS '在线客服访客表';
COMMENT ON COLUMN im_visitor.visitor_key IS '访客唯一标识（前端 localStorage）';
COMMENT ON COLUMN im_visitor.last_active_time IS '最后活跃时间';
CREATE UNIQUE INDEX uk_im_visitor_key ON im_visitor (visitor_key);

-- ------------------------------------------------------------
-- 2. 会话表
-- ------------------------------------------------------------
CREATE TABLE im_conversation (
    id               SERIAL PRIMARY KEY,
    visitor_id       INTEGER NOT NULL,
    cs_admin_id      INTEGER,
    group_id         INTEGER,
    status           SMALLINT NOT NULL DEFAULT 0,
    source           SMALLINT NOT NULL DEFAULT 1,
    started_at       TIMESTAMP,
    ended_at         TIMESTAMP,
    evaluate_score   SMALLINT,
    unread_count     INTEGER  NOT NULL DEFAULT 0,
    last_message     VARCHAR(500),
    last_message_time TIMESTAMP,
    create_time      TIMESTAMP,
    update_time      TIMESTAMP
);
COMMENT ON TABLE  im_conversation IS '在线客服会话表';
COMMENT ON COLUMN im_conversation.visitor_id IS '访客 → im_visitor.id';
COMMENT ON COLUMN im_conversation.cs_admin_id IS '当前接待客服（admin_user.id），待接入时为空';
COMMENT ON COLUMN im_conversation.group_id IS '所属客服分组（im_group.id）';
COMMENT ON COLUMN im_conversation.status IS '状态: 0待接入 1进行中 2转交中 3已结束';
COMMENT ON COLUMN im_conversation.source IS '来源: 1网页 2弹窗 3iframe';
COMMENT ON COLUMN im_conversation.evaluate_score IS '满意度评分 1-5';
COMMENT ON COLUMN im_conversation.unread_count IS '访客侧未读消息数';
CREATE INDEX idx_im_conv_visitor ON im_conversation (visitor_id);
CREATE INDEX idx_im_conv_cs      ON im_conversation (cs_admin_id);
CREATE INDEX idx_im_conv_status  ON im_conversation (status);

-- ------------------------------------------------------------
-- 3. 消息表
-- ------------------------------------------------------------
CREATE TABLE im_message (
    id              SERIAL PRIMARY KEY,
    conversation_id INTEGER NOT NULL,
    sender_type     SMALLINT NOT NULL,
    sender_id       INTEGER,
    msg_type        SMALLINT NOT NULL DEFAULT 1,
    content         TEXT,
    is_recalled     SMALLINT NOT NULL DEFAULT 0,
    extra_json      VARCHAR(500),
    create_time     TIMESTAMP
);
COMMENT ON TABLE  im_message IS '在线客服消息表';
COMMENT ON COLUMN im_message.sender_type IS '发送方类型: 1访客 2客服 3系统';
COMMENT ON COLUMN im_message.msg_type IS '消息类型: 1文字 2图片';
COMMENT ON COLUMN im_message.is_recalled IS '是否撤回: 0否 1是';
CREATE INDEX idx_im_msg_conv ON im_message (conversation_id, id);

-- ------------------------------------------------------------
-- 4. 客服扩展表（基于 admin_user）
-- ------------------------------------------------------------
CREATE TABLE im_customer_service (
    id              SERIAL PRIMARY KEY,
    admin_id        INTEGER NOT NULL,
    group_id        INTEGER,
    service_status  SMALLINT NOT NULL DEFAULT 0,
    max_concurrent  INTEGER  NOT NULL DEFAULT 10,
    current_count   INTEGER  NOT NULL DEFAULT 0,
    welcome_msg     VARCHAR(500),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP
);
COMMENT ON TABLE  im_customer_service IS '在线客服扩展信息表';
COMMENT ON COLUMN im_customer_service.admin_id IS '关联客服（admin_user.id）';
COMMENT ON COLUMN im_customer_service.group_id IS '所属分组（im_group.id）';
COMMENT ON COLUMN im_customer_service.service_status IS '服务状态: 0离线 1空闲 2忙碌 3接待中';
COMMENT ON COLUMN im_customer_service.max_concurrent IS '最大并发接待数';
COMMENT ON COLUMN im_customer_service.current_count IS '当前接待中的会话数';
CREATE UNIQUE INDEX uk_im_cs_admin ON im_customer_service (admin_id);

-- ------------------------------------------------------------
-- 5. 客服分组表
-- ------------------------------------------------------------
CREATE TABLE im_group (
    id             SERIAL PRIMARY KEY,
    name           VARCHAR(50) NOT NULL DEFAULT '',
    description    VARCHAR(255),
    dispatch_rule  SMALLINT NOT NULL DEFAULT 1,
    create_time    TIMESTAMP,
    update_time    TIMESTAMP
);
COMMENT ON TABLE  im_group IS '在线客服分组表';
COMMENT ON COLUMN im_group.dispatch_rule IS '分配规则: 1轮询 2空闲优先';

-- ------------------------------------------------------------
-- 6. 分组-客服关联表
-- ------------------------------------------------------------
CREATE TABLE im_group_member (
    id           SERIAL PRIMARY KEY,
    group_id     INTEGER NOT NULL,
    cs_admin_id  INTEGER NOT NULL,
    create_time  TIMESTAMP
);
COMMENT ON TABLE im_group_member IS '客服分组-成员关联表';
CREATE INDEX idx_im_gm_group ON im_group_member (group_id);
CREATE INDEX idx_im_gm_cs    ON im_group_member (cs_admin_id);

-- ------------------------------------------------------------
-- 7. 常用话术表
-- ------------------------------------------------------------
CREATE TABLE im_quick_reply (
    id          SERIAL PRIMARY KEY,
    group_id    INTEGER,
    title       VARCHAR(100) NOT NULL DEFAULT '',
    content     VARCHAR(2000) NOT NULL DEFAULT '',
    sort        INTEGER NOT NULL DEFAULT 100,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);
COMMENT ON TABLE  im_quick_reply IS '在线客服常用话术表';
COMMENT ON COLUMN im_quick_reply.group_id IS '所属分组（空=公共话术）';
CREATE INDEX idx_im_qr_group ON im_quick_reply (group_id);

-- ------------------------------------------------------------
-- 8. 会话评价表
-- ------------------------------------------------------------
CREATE TABLE im_evaluate (
    id              SERIAL PRIMARY KEY,
    conversation_id INTEGER NOT NULL,
    score           SMALLINT NOT NULL DEFAULT 5,
    tags            VARCHAR(255),
    remark          VARCHAR(500),
    create_time     TIMESTAMP
);
COMMENT ON TABLE  im_evaluate IS '在线客服会话评价表';
COMMENT ON COLUMN im_evaluate.score IS '评分 1-5';
CREATE INDEX idx_im_eval_conv ON im_evaluate (conversation_id);

-- ------------------------------------------------------------
-- 9. 考核统计日级表（阶段三使用，先建出）
-- ------------------------------------------------------------
CREATE TABLE im_stats_daily (
    id               SERIAL PRIMARY KEY,
    cs_admin_id      INTEGER NOT NULL,
    stat_date        DATE NOT NULL,
    conv_count       INTEGER NOT NULL DEFAULT 0,
    msg_count        INTEGER NOT NULL DEFAULT 0,
    avg_response_sec INTEGER NOT NULL DEFAULT 0,
    avg_score        NUMERIC(3,1) NOT NULL DEFAULT 0,
    transfer_count   INTEGER NOT NULL DEFAULT 0
);
COMMENT ON TABLE  im_stats_daily IS '在线客服考核统计日级表';
CREATE UNIQUE INDEX uk_im_stats_cs_date ON im_stats_daily (cs_admin_id, stat_date);

-- ------------------------------------------------------------
-- 10. 系统配置表（入口显隐/电话/工单链接等）
-- ------------------------------------------------------------
CREATE TABLE im_config (
    id           SERIAL PRIMARY KEY,
    config_key   VARCHAR(50) NOT NULL DEFAULT '',
    config_value VARCHAR(500) NOT NULL DEFAULT '',
    remark       VARCHAR(255)
);
COMMENT ON TABLE im_config IS '在线客服系统配置表';
CREATE UNIQUE INDEX uk_im_config_key ON im_config (config_key);

-- ------------------------------------------------------------
-- 在线客服网页嵌入渠道表（将访客端以 iframe / 浮窗接入第三方站点）
--   实体见 ImEmbedChannel.java，publish_token 为长期发布密钥（em_ 前缀），可在管理端轮换。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS im_embed_channels (
    id                    SERIAL       PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL DEFAULT '',       -- 渠道名称（如：官网客服）
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,     -- 是否启用
    publish_token         VARCHAR(64)  NOT NULL,                  -- 长期发布密钥（em_ 前缀），相当于 API Key
    allowed_origins       TEXT,                                  -- 域名白名单，每行一个 origin，留空不限制
    rate_limit_per_minute INTEGER      NOT NULL DEFAULT 60,       -- 每 IP 每分钟请求上限（公开接口）
    rate_limit_per_day    INTEGER      NOT NULL DEFAULT 10000,    -- 每渠道每天请求总量上限
    widget_position       VARCHAR(32)  NOT NULL DEFAULT 'bottom-right',  -- 浮窗位置
    primary_color         VARCHAR(16)  NOT NULL DEFAULT '#07C05F',       -- 主题色 CSS 色值
    page_title            VARCHAR(100) NOT NULL DEFAULT '在线客服',       -- 嵌入页/浮窗标题
    welcome_message       TEXT,                                  -- 默认欢迎语
    sandbox_mode          SMALLINT     NOT NULL DEFAULT 0,        -- 是否强制 iframe sandbox 1是 0否
    create_time           TIMESTAMP,
    update_time           TIMESTAMP
);
COMMENT ON TABLE  im_embed_channels IS '在线客服网页嵌入渠道表';
COMMENT ON COLUMN im_embed_channels.publish_token IS '长期发布密钥，相当于 API Key，可在管理端轮换';
CREATE UNIQUE INDEX uk_im_embed_publish_token ON im_embed_channels (publish_token);

-- ------------------------------------------------------------
-- IM 系统配置默认值：入口显隐 + 电话 + 工单链接 + 标题 + 欢迎语
-- ------------------------------------------------------------
INSERT INTO im_config (config_key, config_value, remark) VALUES
    ('online_chat_show', '1',   '在线客服入口是否显示: 1显示 0隐藏'),
    ('ticket_show',      '1',   '工单入口是否显示: 1显示 0隐藏'),
    ('phone_show',       '1',   '咨询电话入口是否显示: 1显示 0隐藏'),
    ('phone',            '400-888-8888', '咨询电话号码'),
    ('ticket_url',       '/workOrder',   '工单跳转链接'),
    ('portal_title',     '在线客服',     '访客端标题'),
    ('welcome_msg',      '您好，请问有什么可以帮您？', '默认欢迎语');

-- ------------------------------------------------------------
-- AI 客服配置：开关 / 知识库 / 欢迎语
-- ------------------------------------------------------------
INSERT INTO im_config (config_key, config_value, remark) VALUES
    ('ai_enable',  '1',   'AI 客服开关: 1开启 0关闭（开启后会话未接入时由 AI 先答）'),
    ('ai_kb_ids',  '',    'AI 客服使用的知识库 id，逗号分隔（留空则 AI 仅兜底闲聊）'),
    ('ai_welcome', '您好，我是AI助手，有什么可以帮您？', 'AI 客服欢迎语');

-- ------------------------------------------------------------
-- IM 模块默认分组（默认分组 id=1，轮询分配）
-- ------------------------------------------------------------
INSERT INTO im_group (id, name, description, dispatch_rule, create_time, update_time) VALUES
    (1, '默认客服组', '系统默认客服分组', 1, now(), now());

-- ------------------------------------------------------------
-- IM 模块菜单（顶级目录 id=80，二级菜单 + 功能按钮）
--   component 对应 admin/src/views/im/xxx
--   auth 对应后端接口，权限白名单精确匹配
-- ------------------------------------------------------------
INSERT INTO admin_menu (id, pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    -- 一级目录
    (80, 0,  '在线客服', 1, 'im', '#', 'LAYOUT', '#', 'MessageOutlined', 55, 1, now(), now()),

    -- 二级菜单
    (81, 80, '客服工作台', 1, 'workbench', 'workbench', '/im/workbench/index', 'im/conversation/index', '', 95, 1, now(), now()),
    (82, 80, '待接入',     1, 'waiting',   'waiting',   '/im/waiting/index',   'im/queue/waiting',     '', 90, 1, now(), now()),
    (83, 80, '系统配置',   1, 'config',    'config',    '/im/config/index',    'im/config/index',      '', 50, 1, now(), now()),

    -- 工作台 功能按钮(type=2)
    (84, 81, '会话列表',   2, '', '', '', 'im/conversation/index',    '', 95, 1, now(), now()),
    (85, 81, '历史消息',   2, '', '', '', 'im/conversation/messages', '', 90, 1, now(), now()),
    (86, 81, '接入会话',   2, '', '', '', 'im/conversation/claim',    '', 85, 1, now(), now()),
    (87, 81, '结束会话',   2, '', '', '', 'im/conversation/end',      '', 80, 1, now(), now()),
    (88, 81, '未读数',     2, '', '', '', 'im/conversation/unread',   '', 75, 1, now(), now()),

    -- 待接入 功能按钮
    (89, 82, '待接入队列', 2, '', '', '', 'im/queue/waiting', '', 90, 1, now(), now());

-- ------------------------------------------------------------
-- IM 模块序列重置
-- ------------------------------------------------------------
SELECT setval('im_visitor_id_seq',          (SELECT MAX(id) FROM im_visitor));
SELECT setval('im_conversation_id_seq',     (SELECT MAX(id) FROM im_conversation));
SELECT setval('im_message_id_seq',          (SELECT MAX(id) FROM im_message));
SELECT setval('im_customer_service_id_seq', (SELECT MAX(id) FROM im_customer_service));
SELECT setval('im_group_id_seq',            (SELECT MAX(id) FROM im_group));
SELECT setval('im_group_member_id_seq',     (SELECT MAX(id) FROM im_group_member));
SELECT setval('im_quick_reply_id_seq',      (SELECT MAX(id) FROM im_quick_reply));
SELECT setval('im_evaluate_id_seq',         (SELECT MAX(id) FROM im_evaluate));
SELECT setval('im_stats_daily_id_seq',      (SELECT MAX(id) FROM im_stats_daily));
SELECT setval('im_config_id_seq',           (SELECT MAX(id) FROM im_config));
SELECT setval('admin_menu_id_seq',          (SELECT MAX(id) FROM admin_menu));

-- ============================================================
-- 知识库模块菜单（顶级目录 id=100）
--   component 对应 admin/src/views/{knowledge,ai/model}/xxx
--   permissionMode=BACK：前端路由 100% 由 admin_menu(type=1) 驱动，
--   漏配会导致 /knowledge/* 落入 catch-all 404。
--   注意：id=104「知识库详情」是必填项 —— index.vue 的 goDetail() 会
--   router.push('/knowledge/detail?kbId=...')，缺这行会 404。
-- ============================================================
INSERT INTO admin_menu (id, pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    -- 一级目录
    (100, 0,   '知识库',     1, 'knowledge', '#',      'LAYOUT',              '#',                 'BookOutlined', 50, 1, now(), now()),

    -- 二级菜单（type=1）
    (101, 100, '知识库管理', 1, 'kb',        'kb',     '/knowledge/index',    'knowledge/index',   '', 95, 1, now(), now()),
    (102, 100, 'AI模型管理', 1, 'aimodel',   'aimodel','/ai/model/index',     'ai/model/index',    '', 90, 1, now(), now()),
    -- 详情页：带 ?kbId= 参数，由列表页 goDetail() 跳转；无参数直点停在 detail.vue 空状态提示，不会报错
    (104, 100, '知识库详情', 1, 'kbdetail',  'detail', '/knowledge/detail',   '',                  '', 88, 1, now(), now()),
    (160, 100, '意图树管理', 1, 'intent',    'intent', '/knowledge/intent/index', 'knowledge/intent/index', '', 70, 1, now(), now()),

    -- 知识库管理 功能按钮(type=2)
    (110, 101, '知识库列表',   2, '', '', '', 'knowledge/index',            '', 95, 1, now(), now()),
    (111, 101, '新建知识库',   2, '', '', '', 'knowledge/add',              '', 90, 1, now(), now()),
    (112, 101, '编辑知识库',   2, '', '', '', 'knowledge/edit',             '', 85, 1, now(), now()),
    (113, 101, '删除知识库',   2, '', '', '', 'knowledge/del',              '', 80, 1, now(), now()),
    (114, 101, '向量化知识库', 2, '', '', '', 'knowledge/embedding',        '', 75, 1, now(), now()),
    (115, 101, '命中测试',     2, '', '', '', 'knowledge/hitTest',          '', 70, 1, now(), now()),

    -- 文档/段落/问答 功能按钮（type=2）
    (120, 101, '文档列表',     2, '', '', '', 'knowledge/document/list',      '', 95, 1, now(), now()),
    (121, 101, '上传文档',     2, '', '', '', 'knowledge/document/upload',    '', 90, 1, now(), now()),
    (122, 101, '向量化文档',   2, '', '', '', 'knowledge/document/embedding', '', 85, 1, now(), now()),
    (123, 101, '生成问题',     2, '', '', '', 'knowledge/document/question',  '', 80, 1, now(), now()),
    (124, 101, '删除文档',     2, '', '', '', 'knowledge/document/del',       '', 75, 1, now(), now()),
    (125, 101, '片段列表',     2, '', '', '', 'knowledge/paragraph/list',     '', 70, 1, now(), now()),
    (126, 101, '问题列表',     2, '', '', '', 'knowledge/question/list',      '', 65, 1, now(), now()),
    (150, 101, '流水线定义',   2, '', '', '', 'knowledge/pipeline/list',      '', 55, 1, now(), now()),
    (151, 101, '流水线执行',   2, '', '', '', 'knowledge/pipeline/run',       '', 50, 1, now(), now()),

    -- AI模型管理 功能按钮（type=2）
    (130, 102, '模型列表',     2, '', '', '', 'ai/model/list',      '', 95, 1, now(), now()),
    (131, 102, '模型详情',     2, '', '', '', 'ai/model/info',      '', 90, 1, now(), now()),
    (132, 102, '新增模型',     2, '', '', '', 'ai/model/add',       '', 85, 1, now(), now()),
    (133, 102, '编辑模型',     2, '', '', '', 'ai/model/edit',      '', 80, 1, now(), now()),
    (134, 102, '删除模型',     2, '', '', '', 'ai/model/del',       '', 75, 1, now(), now()),
    (135, 102, '启用禁用',     2, '', '', '', 'ai/model/status',    '', 70, 1, now(), now()),
    (136, 102, '测试连接',     2, '', '', '', 'ai/model/test',      '', 65, 1, now(), now()),
    (137, 102, '重排模型列表', 2, '', '', '', 'ai/model/rerankList','', 60, 1, now(), now()),

    -- 意图树 功能按钮（type=2）
    (161, 160, '树查看',       2, '', '', '', 'knowledge/intent/tree',         '', 95, 1, now(), now()),
    (162, 160, '新增节点',     2, '', '', '', 'knowledge/intent/add',          '', 90, 1, now(), now()),
    (163, 160, '编辑节点',     2, '', '', '', 'knowledge/intent/edit',         '', 85, 1, now(), now()),
    (164, 160, '删除节点',     2, '', '', '', 'knowledge/intent/del',          '', 80, 1, now(), now()),
    (165, 160, '批量启停',     2, '', '', '', 'knowledge/intent/batchEnable',  '', 75, 1, now(), now()),

    -- 解析引擎配置（与 AI模型管理 并列，管 MinerU 等非 LLM 外部服务）
    --   component=ai/service/index → admin/src/views/ai/service/index.vue
    (170, 100, '解析引擎', 1, 'aiservice', 'aiservice', '/ai/service/index', 'ai/service/index', '', 65, 1, now(), now()),

    -- 解析引擎配置 功能按钮（type=2，对齐 /ai/service/* 鉴权点）
    (171, 170, '服务列表',     2, '', '', '', 'ai/service/list',   '', 95, 1, now(), now()),
    (172, 170, '新增服务',     2, '', '', '', 'ai/service/add',    '', 90, 1, now(), now()),
    (173, 170, '编辑服务',     2, '', '', '', 'ai/service/edit',   '', 85, 1, now(), now()),
    (174, 170, '删除服务',     2, '', '', '', 'ai/service/del',    '', 80, 1, now(), now()),
    (175, 170, '启用禁用',     2, '', '', '', 'ai/service/status', '', 75, 1, now(), now()),
    (176, 170, '测试连接',     2, '', '', '', 'ai/service/test',   '', 70, 1, now(), now());

-- 知识库详情页（id=104）不在侧边栏显示，仅注册为路由供列表页 goDetail() 跳转。
-- 该 INSERT 段未带 hidden 列（DEFAULT 0），此处显式置 1。
UPDATE admin_menu SET hidden = 1 WHERE id = 104;

SELECT setval('admin_menu_id_seq', (SELECT MAX(id) FROM admin_menu));

-- ============================================================
-- AI 模型配置表（对话/向量/重排/视觉，多候选模型容错降级链）
--   实体见 AiModel.java，页面可编辑，运行时从本表读取多候选模型。
--   type: 1=对话 2=向量/嵌入 3=重排 4=视觉(VLM)。
--   credential / options 为 JSON 文本（页面动态渲染字段，原样落库）。
--   被 knowledge_base.embedding_model_id / kg_config / sample_query_config 引用，故先建于它们之前。
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_model (
    id                 SERIAL       PRIMARY KEY,
    name               VARCHAR(100) NOT NULL DEFAULT '',          -- 模型厂家/名称（如 OpenAI、通义千问、Ollama）
    type               SMALLINT     NOT NULL DEFAULT 1,           -- 1对话 2向量 3重排 4视觉(VLM)
    provider           VARCHAR(64)  NOT NULL DEFAULT '',          -- openai / ollama 等
    credential         TEXT,                                      -- 凭证 JSON 数组（[{"field":"apiKey","value":"sk-xxx"}]）
    models             TEXT,                                      -- 可用模型名（逗号分隔）；重排模型只允许一个
    function_calling   TEXT,                                      -- 函数调用能力（逗号分隔，对话模型用）
    options            TEXT,                                      -- 选项 JSON 数组（url/temperature/maxOutputTokens 等）
    status             SMALLINT     NOT NULL DEFAULT 1,           -- 1启用 2禁用
    priority           INTEGER      NOT NULL DEFAULT 100,         -- 候选优先级（数值小者优先，多模型容错降级用）
    supports_thinking  SMALLINT     NOT NULL DEFAULT 0,           -- 是否支持深度思考 0否 1是
    create_time        TIMESTAMP,
    update_time        TIMESTAMP
);
COMMENT ON TABLE  ai_model IS 'AI 模型配置表';
COMMENT ON COLUMN ai_model.type      IS '1对话 2向量 3重排 4视觉(VLM)';
COMMENT ON COLUMN ai_model.credential IS '凭证 JSON 数组';
COMMENT ON COLUMN ai_model.options   IS '选项 JSON 数组：url/temperature/maxOutputTokens 等';
COMMENT ON COLUMN ai_model.priority  IS '候选优先级，数值小者优先';
CREATE INDEX idx_ai_model_type_status ON ai_model (type, status);

-- ============================================================
-- 解析引擎配置表（MinerU 解析引擎等，按 category 分类，config 存 JSON）
--   与 ai_model 区分：ai_model 管 LLM 模型（对话/向量/重排/视觉，含容错降级链），
--   本表管非 LLM 的解析引擎（PDF 解析引擎、OCR 等，字段差异大故独立建表）。
--   category 取值：mineru_self / mineru_cloud / 未来其他。
--   config JSON 的 schema 按 category 不同（详见 IExtServiceConfigService 类注释）。
--   运行时 MinerUParserFactory 按 engine=mineru/mineru_cloud 查本表对应 category +
--   status=1 的记录，构造 MinerUOptions（替代原 application.yml 的 app.rag.mineru.*）。
-- ============================================================
DROP TABLE IF EXISTS ext_service_config CASCADE;
CREATE TABLE ext_service_config (
    id           SERIAL       PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,                 -- 配置名称（如「自建MinerU」）
    category     VARCHAR(50)  NOT NULL,                 -- 服务类别 mineru_self/mineru_cloud/...
    config       TEXT,                                  -- 配置 JSON（按 category 定 schema）
    remark       VARCHAR(255),                          -- 备注
    status       SMALLINT     NOT NULL DEFAULT 1,       -- 1启用 2禁用
    sort         INTEGER      NOT NULL DEFAULT 100,     -- 排序（数值小者靠前）
    create_time  TIMESTAMP    DEFAULT now(),
    update_time  TIMESTAMP    DEFAULT now()
);
CREATE INDEX idx_ext_service_category ON ext_service_config(category);
COMMENT ON TABLE  ext_service_config IS '外部服务配置表';
COMMENT ON COLUMN ext_service_config.category IS '服务类别：mineru_self 自建MinerU / mineru_cloud 云端MinerU / 未来扩展';
COMMENT ON COLUMN ext_service_config.config   IS '配置 JSON，schema 按 category 不同（详见 IExtServiceConfigService）';

-- ============================================================
-- 增量迁移（已部署环境按需执行，幂等）
-- ============================================================

-- 0) admin_menu 补 hidden 列（是否在侧边栏隐藏 0:显示 1:隐藏，已部署库兼容补丁，幂等）
ALTER TABLE admin_menu ADD COLUMN IF NOT EXISTS hidden SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN admin_menu.hidden IS '是否在侧边栏隐藏 0:显示 1:隐藏（仍注册路由，可URL直访，如知识库详情页）';
-- 知识库详情页（id=104）不在侧边栏显示，仅注册为路由供列表页 goDetail() 跳转
UPDATE admin_menu SET hidden = 1 WHERE id = 104;

-- 1) 下线「问答测试」菜单及子按钮（前端页面与后端 /knowledge/chat/* 端点已移除）
DELETE FROM admin_menu WHERE id IN (103, 140, 141);
-- 注：权限按 admin_role.menu（逗号分隔 id 串）存储，无关联表。
--     删菜单后，已分配这些 id 的角色菜单列表里会残留死 id，
--     前端按 id 渲染时找不到记录会自动忽略，不影响功能；超管 menu='*' 不受影响。

-- 2) 补建「外部服务配置」菜单（MinerU 解析引擎等非 LLM 外部服务入口）
--    若已存在则跳过。id=170 主菜单 + 171~176 功能按钮。
INSERT INTO admin_menu (id, pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT 170, 100, '外部服务配置', 1, 'aiservice', 'aiservice', '/ai/service/index', 'ai/service/index', '', 65, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE id = 170);

INSERT INTO admin_menu (id, pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    (171, 170, '服务列表', 2, '', '', '', 'ai/service/list',   '', 95, 1, now(), now()),
    (172, 170, '新增服务', 2, '', '', '', 'ai/service/add',    '', 90, 1, now(), now()),
    (173, 170, '编辑服务', 2, '', '', '', 'ai/service/edit',   '', 85, 1, now(), now()),
    (174, 170, '删除服务', 2, '', '', '', 'ai/service/del',    '', 80, 1, now(), now()),
    (175, 170, '启用禁用', 2, '', '', '', 'ai/service/status', '', 75, 1, now(), now()),
    (176, 170, '测试连接', 2, '', '', '', 'ai/service/test',   '', 70, 1, now(), now())
ON CONFLICT (id) DO NOTHING;

SELECT setval('admin_menu_id_seq', (SELECT MAX(id) FROM admin_menu));

SELECT setval('ext_service_config_id_seq', (SELECT MAX(id) FROM ext_service_config));

-- ============================================================
-- 样例查询（Sample Query）
-- 录入常用 Q&A 问答对，问题向量化后供问答时做相似度检索，
-- 命中阈值即直接返回预设答案（不走大模型）。
--   sample_query         问答对（问题/答案/向量/tsv/向量化标记，向量自包含，不依赖 chunks 表）
--   sample_query_config  全局配置（embedding 模型 + 相似度阈值，整个功能共用一套）
-- ★ 向量独立存在本表 embedding 列（pgvector），不污染 chunks 表；
--   embedding 列不固定维度（裸 vector 类型），切换模型时新旧维度并存不影响已有数据。
-- ============================================================
CREATE TABLE IF NOT EXISTS sample_query (
    id              BIGSERIAL    PRIMARY KEY,
    question        TEXT         NOT NULL,                       -- 问题文本（参与向量化）
    answer          TEXT         NOT NULL,                       -- 答案文本（不向量化）
    embedding       vector,                                      -- 问题向量（pgvector，向量化后回填）
    tsv              tsvector,                                   -- 全文检索（关键词命中）
    vectorized      SMALLINT     NOT NULL DEFAULT 0,             -- 0未向量化 1已向量化
    source          VARCHAR(32)  NOT NULL DEFAULT 'manual',      -- manual / import
    status          SMALLINT     NOT NULL DEFAULT 1,             -- 1启用 2禁用
    created_at      TIMESTAMP    DEFAULT now(),
    updated_at      TIMESTAMP    DEFAULT now()
);
-- ★ 向量索引不在建表时创建（pgvector 要求 embedding 列有维度或表中有数据后才能建，
--   且 IVFFlat 需先有数据构建聚类中心，空表建的索引无效）。
--   等第一批样例向量化完成（vectorized=1 行数明显多于 lists=100）后，手动执行：
--   CREATE INDEX idx_sample_query_embedding ON sample_query USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100) WHERE vectorized = 1;
--   切换 embedding 模型（维度变化）后也需 DROP + 重建该索引。
-- 全文检索索引
CREATE INDEX IF NOT EXISTS idx_sample_query_tsv ON sample_query USING gin (tsv);
COMMENT ON TABLE  sample_query IS '样例查询问答对表';
COMMENT ON COLUMN sample_query.question    IS '问题文本，参与向量化，作为检索输入';
COMMENT ON COLUMN sample_query.answer      IS '答案文本，命中后直接返回（不向量化）';
COMMENT ON COLUMN sample_query.embedding   IS '问题向量（pgvector），独立存储，不写入 chunks 表';
COMMENT ON COLUMN sample_query.vectorized  IS '0未向量化 1已向量化（embedding 是否已填充）';

CREATE TABLE IF NOT EXISTS sample_query_config (
    id                   SERIAL       PRIMARY KEY,
    embedding_model_id   INTEGER,                                  -- 关联 ai_model.id（type=2 向量模型）
    embedding_model_name VARCHAR(128),                             -- 冗余快照：具体模型名（models 中的某一项）
    similarity_threshold NUMERIC(4,3) NOT NULL DEFAULT 0.850,      -- 命中相似度阈值（0~1）
    updated_at           TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  sample_query_config IS '样例查询全局配置表';
COMMENT ON COLUMN sample_query_config.embedding_model_id   IS '关联 ai_model.id（type=2 向量模型）';
COMMENT ON COLUMN sample_query_config.embedding_model_name IS '冗余快照：具体模型名（ai_model.models 中的某一项）';
COMMENT ON COLUMN sample_query_config.similarity_threshold IS '命中相似度阈值（0~1），高于此值直接返回答案';
-- 初始化唯一一条全局配置（id 固定 1，方便 upsert）
INSERT INTO sample_query_config (id, similarity_threshold)
SELECT 1, 0.850
WHERE NOT EXISTS (SELECT 1 FROM sample_query_config WHERE id = 1);

-- 3) 补建「样例查询」菜单（挂在知识库目录 pid=100 下，与外部服务配置并列）
--    若已存在则跳过。id=180 主菜单 + 181~188 功能按钮。
INSERT INTO admin_menu (id, pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT 180, 100, '样例查询', 1, 'samplequery', 'sample-query', '/knowledge/sampleQuery/index', 'knowledge/sampleQuery/index', '', 55, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE id = 180);

INSERT INTO admin_menu (id, pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    (181, 180, '查询列表', 2, '', '', '', 'knowledge/sampleQuery/list',   '', 95, 1, now(), now()),
    (182, 180, '新增查询', 2, '', '', '', 'knowledge/sampleQuery/add',    '', 90, 1, now(), now()),
    (183, 180, '编辑查询', 2, '', '', '', 'knowledge/sampleQuery/edit',   '', 85, 1, now(), now()),
    (184, 180, '删除查询', 2, '', '', '', 'knowledge/sampleQuery/del',    '', 80, 1, now(), now()),
    (185, 180, '导入查询', 2, '', '', '', 'knowledge/sampleQuery/import', '', 75, 1, now(), now()),
    (186, 180, '导出查询', 2, '', '', '', 'knowledge/sampleQuery/export', '', 70, 1, now(), now()),
    (187, 180, '向量化',   2, '', '', '', 'knowledge/sampleQuery/vectorize', '', 65, 1, now(), now()),
    (188, 180, '配置',     2, '', '', '', 'knowledge/sampleQuery/config', '', 60, 1, now(), now())
ON CONFLICT (id) DO NOTHING;

SELECT setval('admin_menu_id_seq', (SELECT MAX(id) FROM admin_menu));

-- ============================================================
-- 知识图谱（Knowledge Graph）
-- 对齐 WeKnora 架构：实体向量在 PostgreSQL（kg_entity + pgvector），
-- 图谱关系在 Neo4j（langchain4j-neo4j 的 Neo4jGraph 管理）。
--   kg_config            全局配置（抽取 LLM + 向量模型 + 阈值/跳数，整个功能共用 id=1）
--   kg_entity            实体向量索引（pgvector，与 chunks 同库，独立表）
--   kg_extraction_record 文档级抽取状态（pending/extracting/done/failed）
-- Neo4j 侧：节点标签 Entity、唯一约束 (kb_id, canonical_name) 由 KnowledgeGraphConfig 启动时建。
-- ★ 实体向量独立存在 kg_entity.embedding 列（pgvector 裸 vector，不固定维度）；
--   全局开关 app.rag.knowledge-graph.enabled=false 时 KG Bean 不装配，零侵入。
-- ============================================================
CREATE TABLE IF NOT EXISTS kg_config (
    id                    SERIAL       PRIMARY KEY,
    extract_model_id      INTEGER,                                    -- 关联 ai_model.id（type=1 对话模型，用于抽实体/关系）
    extract_model_name    VARCHAR(128),                               -- 冗余快照：具体模型名
    embedding_model_id    INTEGER,                                    -- 关联 ai_model.id（type=2 向量模型，用于实体向量化）
    embedding_model_name  VARCHAR(128),                               -- 冗余快照：具体模型名
    enabled               SMALLINT     NOT NULL DEFAULT 2,            -- 全局开关 1=启用 2=禁用
    similarity_threshold  NUMERIC(4,3) NOT NULL DEFAULT 0.650,        -- 实体向量召回相似度阈值（0~1）
    extract_batch_size    INTEGER      NOT NULL DEFAULT 5,            -- 单次 LLM 调用合并抽取的父块数
    hop_depth             SMALLINT     NOT NULL DEFAULT 2,            -- 子图跳数 1 或 2（默认 2 跳，二跳按 second_hop_weight 降权）
    second_hop_weight     NUMERIC(3,2) NOT NULL DEFAULT 0.50,         -- 二跳关系衰减权重（0~1）
    entity_merge_threshold NUMERIC(4,3) NOT NULL DEFAULT 0.880,        -- 实体 embedding 合并阈值（第三期 EntityDisambiguator 用）
    retrieval_mode        VARCHAR(16)  NOT NULL DEFAULT 'local',       -- 第四期：图谱检索模式 local/global/hybrid
    community_enabled     SMALLINT     NOT NULL DEFAULT 2,             -- 第四期：社区检测开关 1=启用 2=禁用
    created_at            TIMESTAMP    DEFAULT now(),
    updated_at            TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  kg_config IS '知识图谱全局配置表';
COMMENT ON COLUMN kg_config.extract_model_id     IS '关联 ai_model.id（type=1 对话模型，用于抽实体/关系）';
COMMENT ON COLUMN kg_config.embedding_model_id   IS '关联 ai_model.id（type=2 向量模型，用于实体向量化）';
COMMENT ON COLUMN kg_config.enabled              IS '全局开关 1=启用 2=禁用（与 app.rag.knowledge-graph.enabled 双闸）';
COMMENT ON COLUMN kg_config.similarity_threshold IS '实体向量召回相似度阈值（0~1），高于此值才视为命中';
COMMENT ON COLUMN kg_config.hop_depth            IS '子图跳数 1=一跳 2=二跳（带 second_hop_weight 衰减）';
COMMENT ON COLUMN kg_config.entity_merge_threshold IS '实体 embedding 合并阈值（0~1，默认 0.88）：字符串消歧之后，对实体 name+description 算余弦相似度，>= 此值合并';
COMMENT ON COLUMN kg_config.retrieval_mode       IS '图谱检索模式 local（子图扩展）/ global（社区摘要召回）/ hybrid（双路并行）';
COMMENT ON COLUMN kg_config.community_enabled    IS '社区检测开关 1=启用 2=禁用（global/hybrid 模式前置）';
-- 初始化唯一一条全局配置（id 固定 1）
INSERT INTO kg_config (id, similarity_threshold, extract_batch_size, hop_depth, second_hop_weight, entity_merge_threshold, retrieval_mode, community_enabled)
SELECT 1, 0.650, 5, 2, 0.50, 0.880, 'local', 2
WHERE NOT EXISTS (SELECT 1 FROM kg_config WHERE id = 1);

-- 增量迁移：第三期新增 entity_merge_threshold 列（已建库的兼容补丁，IF NOT EXISTS 幂等）
ALTER TABLE kg_config ADD COLUMN IF NOT EXISTS entity_merge_threshold NUMERIC(4,3) NOT NULL DEFAULT 0.880;
COMMENT ON COLUMN kg_config.entity_merge_threshold IS '实体 embedding 合并阈值（0~1，默认 0.88）：字符串消歧之后，对实体 name+description 算余弦相似度，>= 此值合并';
-- 增量迁移：第四期新增 retrieval_mode + community_enabled 列
ALTER TABLE kg_config ADD COLUMN IF NOT EXISTS retrieval_mode VARCHAR(16) NOT NULL DEFAULT 'local';
ALTER TABLE kg_config ADD COLUMN IF NOT EXISTS community_enabled SMALLINT NOT NULL DEFAULT 2;
COMMENT ON COLUMN kg_config.retrieval_mode    IS '图谱检索模式 local（子图扩展）/ global（社区摘要召回）/ hybrid（双路并行）';
COMMENT ON COLUMN kg_config.community_enabled IS '社区检测开关 1=启用 2=禁用（global/hybrid 模式前置）';

CREATE TABLE IF NOT EXISTS kg_entity (
    id                 BIGSERIAL    PRIMARY KEY,
    kb_id              VARCHAR(32)  NOT NULL,                          -- 关联知识库
    doc_id             VARCHAR(64)  NOT NULL DEFAULT '',               -- 关联文档（文档级隔离键，同名实体跨文档各自独立）
    name               TEXT         NOT NULL,                          -- 实体名（原始抽取值）
    canonical_name     TEXT,                                            -- 消歧后规范名（文档内合并同义实体的键）
    entity_type        VARCHAR(64),                                     -- 实体类型：人/组织/产品/概念/...
    description        TEXT,                                            -- 实体描述（参与向量化）
    aliases            TEXT,                                            -- 别名 JSON 数组 ["北京","帝都"]
    neo4j_element_id   VARCHAR(128),                                    -- 对应 Neo4j 节点 elementId（可视化联动用）
    source_doc_ids     TEXT,                                            -- 来源文档 JSON 数组（兼容旧字段，文档隔离下恒为 [doc_id]）
    source_parent_ids  TEXT,                                            -- 来源父块 JSON 数组
    embedding          vector,                                          -- 实体向量（name+description 向量化，pgvector）
    tsv                tsvector,                                        -- 全文检索
    vectorized         SMALLINT     NOT NULL DEFAULT 0,                 -- 0未向量化 1已向量化
    status             SMALLINT     NOT NULL DEFAULT 1,                 -- 1启用 2禁用
    created_at         TIMESTAMP    DEFAULT now(),
    updated_at         TIMESTAMP    DEFAULT now()
);
-- ★ 向量索引不在建表时创建（pgvector 要求列有维度或有数据后才能建）。
--   等第一批实体向量化完成后，手动执行：
--   CREATE INDEX idx_kg_entity_embedding ON kg_entity USING hnsw (embedding vector_cosine_ops) WHERE vectorized = 1;
CREATE INDEX IF NOT EXISTS idx_kg_entity_tsv ON kg_entity USING gin (tsv);
CREATE INDEX IF NOT EXISTS idx_kg_entity_kb ON kg_entity (kb_id);
CREATE INDEX IF NOT EXISTS idx_kg_entity_doc ON kg_entity (kb_id, doc_id);
CREATE INDEX IF NOT EXISTS idx_kg_entity_canonical ON kg_entity (kb_id, doc_id, canonical_name);
COMMENT ON TABLE  kg_entity IS '知识图谱实体向量索引表';
COMMENT ON COLUMN kg_entity.doc_id           IS '关联文档 id（文档级隔离键，同名实体跨文档各自独立）';
COMMENT ON COLUMN kg_entity.canonical_name   IS '消歧后规范名，作为 Neo4j 节点唯一键的一部分 (kb_id, doc_id, canonical_name)';
COMMENT ON COLUMN kg_entity.aliases           IS '别名 JSON 数组，支持同义实体召回';
COMMENT ON COLUMN kg_entity.neo4j_element_id  IS '对应 Neo4j 节点 elementId，可视化联动用';
COMMENT ON COLUMN kg_entity.source_parent_ids IS '来源父块 JSON 数组（parent_chunks.id）';
COMMENT ON COLUMN kg_entity.embedding         IS '实体向量（name+description 向量化），独立存储不写入 chunks';

-- 已有库升级：补 doc_id 列（文档级隔离改造）
ALTER TABLE kg_entity ADD COLUMN IF NOT EXISTS doc_id VARCHAR(64) NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS idx_kg_entity_doc ON kg_entity (kb_id, doc_id);
COMMENT ON COLUMN kg_entity.doc_id IS '关联文档 id（文档级隔离键，同名实体跨文档各自独立）';

CREATE TABLE IF NOT EXISTS kg_extraction_record (
    id              BIGSERIAL    PRIMARY KEY,
    kb_id           VARCHAR(32)  NOT NULL,                              -- 关联知识库
    document_id     VARCHAR(64)  NOT NULL,                              -- 关联文档（doc_xxx）
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending',            -- pending/extracting/done/failed
    parent_total    INTEGER      NOT NULL DEFAULT 0,                    -- 待抽取父块总数
    parent_done     INTEGER      NOT NULL DEFAULT 0,                    -- 已完成父块数
    entity_count    INTEGER      NOT NULL DEFAULT 0,                    -- 抽取实体数（累计）
    relation_count  INTEGER      NOT NULL DEFAULT 0,                    -- 抽取关系数（累计）
    error_msg       TEXT,                                             -- 失败原因（status=failed 时）
    started_at      TIMESTAMP,                                        -- 抽取开始时间
    finished_at     TIMESTAMP,                                        -- 抽取完成时间
    created_at      TIMESTAMP    DEFAULT now(),
    updated_at      TIMESTAMP    DEFAULT now(),
    UNIQUE (kb_id, document_id)
);
CREATE INDEX IF NOT EXISTS idx_kg_record_kb ON kg_extraction_record (kb_id);
CREATE INDEX IF NOT EXISTS idx_kg_record_status ON kg_extraction_record (status);
COMMENT ON TABLE  kg_extraction_record IS '知识图谱文档级抽取状态记录表';
COMMENT ON COLUMN kg_extraction_record.status         IS 'pending 待处理 / extracting 抽取中 / done 完成 / failed 失败';
COMMENT ON COLUMN kg_extraction_record.parent_total   IS '待抽取父块总数（来自 parent_chunks by document_id）';

-- knowledge_base 知识库主表（移植自 sparkxV2，扩展 embedding_model_id/dimension）
-- 实体见 KnowledgeBase.java，用 @TableName 映射，MyBatis-Plus 不自动建表，须在此显式 CREATE。
-- CREATE TABLE IF NOT EXISTS 保证幂等：已部署环境（sparkxV2 存量表）执行时不会重建、不丢数据。
CREATE TABLE IF NOT EXISTS knowledge_base (
    id                       VARCHAR(64)  PRIMARY KEY,                 -- UUID，业务生成
    name                     VARCHAR(128) NOT NULL DEFAULT '',         -- 知识库名称
    description              TEXT,                                     -- 描述
    embedding_model_id       INTEGER,                                  -- 关联 ai_model.id（确定服务地址/凭证）
    embedding_model_name     VARCHAR(128),                             -- 绑定具体模型名（ai_model.models 中的某一项）
    embedding_model          VARCHAR(64),                              -- 冗余：嵌入模型显示名（展示用）
    embedding_model_url      VARCHAR(512),                             -- 创建时快照的服务地址（来自 ai_model.options.url）
    embedding_model_api_key  VARCHAR(512),                             -- 创建时快照的凭证（来自 ai_model.credential.apiKey）
    dimension                INTEGER,                                  -- 该知识库的向量维度（创建时按绑定 embedding 模型记录）
    status                   SMALLINT     NOT NULL DEFAULT 1,          -- 1正常 2禁用
    doc_count                INTEGER      NOT NULL DEFAULT 0,          -- 文档数（冗余，列表展示）
    kg_enabled               SMALLINT     NOT NULL DEFAULT 2,          -- 知识图谱开关 1=启用 2=禁用
    created_at               TIMESTAMP    DEFAULT now(),
    updated_at               TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  knowledge_base IS '知识库主表';
COMMENT ON COLUMN knowledge_base.id                      IS '主键';
COMMENT ON COLUMN knowledge_base.embedding_model_id      IS '绑定的嵌入模型 ai_model.id';
COMMENT ON COLUMN knowledge_base.embedding_model_name    IS '绑定的具体模型名';
COMMENT ON COLUMN knowledge_base.embedding_model         IS '冗余：嵌入模型显示名';
COMMENT ON COLUMN knowledge_base.embedding_model_url     IS '创建时快照的服务地址';
COMMENT ON COLUMN knowledge_base.embedding_model_api_key IS '创建时快照的凭证';
COMMENT ON COLUMN knowledge_base.dimension               IS '向量维度';
COMMENT ON COLUMN knowledge_base.status                  IS '1正常 2禁用';
COMMENT ON COLUMN knowledge_base.doc_count               IS '文档数';
COMMENT ON COLUMN knowledge_base.kg_enabled              IS '知识图谱开关 1=启用 2=禁用';

-- ============================================================
-- 知识库文档表（移植自 sparkxV2 document，扩展 question_status/active）
--   实体见 KnowledgeDocument.java。
--   ★ ingestion_summary 为 jsonb：BaseMapper 的 insert/updateById 不写它（insertStrategy=NEVER），
--     落库走 KnowledgeDocumentMapper.updateIngestionSummary 的原生 SQL + CAST(... AS jsonb)。
-- ============================================================
CREATE TABLE IF NOT EXISTS document (
    id                 VARCHAR(64)  PRIMARY KEY,                  -- UUID，业务生成
    kb_id              VARCHAR(64)  NOT NULL,                     -- 关联 knowledge_base.id
    file_name          VARCHAR(255) NOT NULL DEFAULT '',          -- 文件名
    file_size          BIGINT,                                    -- 文件大小（字节）
    storage_url        VARCHAR(512),                              -- MinIO 对象 key
    ingestion_summary  JSONB,                                     -- 入库耗时统计 JSON（jsonb，走原生 SQL 落库）
    status             VARCHAR(20)  NOT NULL DEFAULT 'pending',   -- 向量化状态 pending|processing|done|failed
    chunk_count        INTEGER      NOT NULL DEFAULT 0,           -- 切块数
    question_status    SMALLINT     NOT NULL DEFAULT 1,           -- 问题生成状态 1待生成 2生成中 3已生成
    active             SMALLINT     NOT NULL DEFAULT 1,           -- 是否启用 1正常 2禁用
    kg_enabled         SMALLINT     NOT NULL DEFAULT 2,           -- 知识图谱开关 1=启用 2=禁用（文档级，决定是否参与抽取/召回）
    created_at         TIMESTAMP    DEFAULT now(),
    updated_at         TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  document IS '知识库文档表';
COMMENT ON COLUMN document.kb_id             IS '关联 knowledge_base.id';
COMMENT ON COLUMN document.storage_url        IS 'MinIO 对象 key';
COMMENT ON COLUMN document.ingestion_summary  IS '入库耗时统计 JSON';
COMMENT ON COLUMN document.status             IS '向量化状态 pending|processing|done|failed';
COMMENT ON COLUMN document.question_status    IS '问题生成状态 1待生成 2生成中 3已生成';
COMMENT ON COLUMN document.active             IS '是否启用 1正常 2禁用';
COMMENT ON COLUMN document.kg_enabled         IS '知识图谱开关 1=启用 2=禁用（文档级）';

-- 已有库升级：补 kg_enabled 列（首次部署时 document 表已存在，此 ALTER 幂等失败可忽略）
ALTER TABLE document ADD COLUMN IF NOT EXISTS kg_enabled SMALLINT NOT NULL DEFAULT 2;
COMMENT ON COLUMN document.kg_enabled IS '知识图谱开关 1=启用 2=禁用（文档级）';
CREATE INDEX idx_document_kb ON document (kb_id);

-- ============================================================
-- 知识库子块表（移植自 sparkxV2 chunks，RAG 核心）
--   实体见 ChunkEntity.java。
--   ★ embedding / tsv 不在实体声明（pgvector/tsvector 类型 JDBC 不认），向量入库与检索走原生 SQL。
--   ★ metadata 为 jsonb：document_id / file_name / type('question') / parentId / chunkRole('child') 等。
--   ★ embedding 为裸 vector（不固定维度），对齐 kg_entity / sample_query。
-- ============================================================
CREATE TABLE IF NOT EXISTS chunks (
    id          VARCHAR(64)  PRIMARY KEY,                         -- UUID，业务生成
    kb_id       VARCHAR(64)  NOT NULL,                            -- 关联 knowledge_base.id
    content     TEXT,                                            -- 分块文本
    embedding   vector,                                          -- 分块向量（pgvector 裸 vector，不固定维度）
    tsv         tsvector,                                        -- 全文检索（HanLP tokenize 后回填）
    metadata    JSONB,                                           -- JSON：document_id/file_name/type/parentId/chunkRole 等
    created_at  TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  chunks IS '知识库子块表';
COMMENT ON COLUMN chunks.kb_id     IS '关联 knowledge_base.id';
COMMENT ON COLUMN chunks.embedding IS '分块向量';
COMMENT ON COLUMN chunks.tsv       IS '全文检索向量';
COMMENT ON COLUMN chunks.metadata  IS 'JSON 元数据';
-- ★ 向量索引不在建表时创建（pgvector 要求 embedding 列有维度或有数据后才能建）。
--   等第一批数据向量化完成后，手动执行：
--   CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);
--   切换 embedding 模型（维度变化）后需 DROP + 重建。
CREATE INDEX idx_chunks_tsv ON chunks USING gin (tsv);
CREATE INDEX idx_chunks_kb  ON chunks (kb_id);
-- 按文档维度过滤（意图节点限定文档检索：metadata->>'document_id' IN (...)）
CREATE INDEX IF NOT EXISTS idx_chunks_doc ON chunks ((metadata->>'document_id'));

-- ============================================================
-- 父块表（移植自 sparkxV2 parent_chunks）
--   只存全文，不存向量（不参与检索，仅被子块命中后展开）。实体见 ParentChunkEntity.java。
--   metadata 为 jsonb：document_id / file_name / parentId 等。
-- ============================================================
CREATE TABLE IF NOT EXISTS parent_chunks (
    id          VARCHAR(64)  PRIMARY KEY,                         -- UUID，业务生成
    kb_id       VARCHAR(64)  NOT NULL,                            -- 关联 knowledge_base.id
    content     TEXT,                                            -- 父块全文
    metadata    JSONB,                                           -- JSON：document_id/file_name 等
    created_at  TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  parent_chunks IS '父块表';
COMMENT ON COLUMN parent_chunks.metadata IS 'JSON：document_id/file_name 等';
CREATE INDEX idx_parent_chunks_kb ON parent_chunks (kb_id);

-- ============================================================
-- 知识库问题表（增加召回成功率，非 QA 对，无答案）
--   实体见 KnowledgeQuestion.java，由"生成问题"功能自动产生或手动录入，关联 question chunk。
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_question (
    id           BIGSERIAL    PRIMARY KEY,
    kb_id        VARCHAR(64)  NOT NULL,                           -- 关联 knowledge_base.id
    document_id  VARCHAR(64),                                     -- 来源文档
    chunk_id     VARCHAR(64),                                     -- 关联问题 chunk chunks.id
    content      TEXT        NOT NULL,                            -- 问题文本
    source       VARCHAR(16) NOT NULL DEFAULT 'manual',           -- manual 手动录入 / ai 生成
    status       SMALLINT    NOT NULL DEFAULT 1,                  -- 1正常 2禁用
    created_at   TIMESTAMP   DEFAULT now(),
    updated_at   TIMESTAMP   DEFAULT now()
);
COMMENT ON TABLE  knowledge_question IS '知识库问题表';
COMMENT ON COLUMN knowledge_question.chunk_id IS '关联问题 chunk chunks.id';
CREATE INDEX idx_knowledge_question_kb ON knowledge_question (kb_id);

-- ============================================================
-- 意图节点表（移植自 sparkxV2 t_intent_node，扁平单层表）
--   实体见 IntentNodeEntity.java。level 字段保留兼容旧数据（早期三级树遗留），现统一为 0；kind: KB/SYSTEM/MCP。
-- ============================================================
CREATE TABLE IF NOT EXISTS t_intent_node (
    id                    VARCHAR(64)  PRIMARY KEY,               -- UUID，业务生成
    parent_id             VARCHAR(64),                            -- 父节点 id，根为 NULL
    level                 SMALLINT     NOT NULL DEFAULT 0,        -- 0=DOMAIN 1=CATEGORY 2=TOPIC
    kind                  VARCHAR(16)  NOT NULL DEFAULT 'KB',     -- KB / SYSTEM / MCP
    name                  VARCHAR(128) NOT NULL DEFAULT '',       -- 节点名称
    description           TEXT,                                  -- 描述
    examples              TEXT,                                  -- 典型问法 JSON
    collection_name       VARCHAR(128),                           -- KB：对应 collection_name
    doc_ids               TEXT,                                   -- KB：限定文档ID JSON 数组，为空检索整库
    mcp_tool_id           VARCHAR(64),                            -- MCP：工具 id
    prompt_template       TEXT,                                  -- 意图级回答模板覆盖
    param_prompt_template TEXT,                                  -- MCP 参数提取自定义
    top_k                 INTEGER,                               -- top_k
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,     -- 是否启用
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,    -- 软删除
    created_at            TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  t_intent_node IS '意图树节点表';
COMMENT ON COLUMN t_intent_node.level  IS '0=DOMAIN 1=CATEGORY 2=TOPIC';
COMMENT ON COLUMN t_intent_node.kind   IS 'KB / SYSTEM / MCP';
CREATE INDEX idx_intent_node_parent ON t_intent_node (parent_id);

-- ============================================================
-- 入库流水线定义节点表（移植自 sparkxV2 t_ingestion_pipeline_node）
--   实体见 IngestionPipelineNode.java。DB 驱动节点连线：fetcher→parser→[enhancer]→chunker→[enricher]→indexer。
-- ============================================================
CREATE TABLE IF NOT EXISTS t_ingestion_pipeline_node (
    id             BIGSERIAL    PRIMARY KEY,
    pipeline_id    VARCHAR(64)  NOT NULL,                         -- 流水线 id
    node_id        VARCHAR(64)  NOT NULL,                         -- 节点 id
    node_type      VARCHAR(32)  NOT NULL DEFAULT '',              -- fetcher/parser/enhancer/chunker/enricher/indexer
    next_node_id   VARCHAR(64),                                   -- 连线：下一节点 id
    settings_json  TEXT,                                          -- 节点配置 JSON
    condition_json TEXT,                                          -- 条件执行（规则 DSL）JSON
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,            -- 是否启用
    created_at     TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  t_ingestion_pipeline_node IS '入库流水线定义节点表';
COMMENT ON COLUMN t_ingestion_pipeline_node.node_type IS 'fetcher/parser/enhancer/chunker/enricher/indexer';
CREATE INDEX idx_pipeline_node_pid ON t_ingestion_pipeline_node (pipeline_id);

-- ============================================================
-- 入库任务节点日志表（移植自 sparkxV2 t_ingestion_task_node）
--   实体见 IngestionTaskNode.java。每节点独立日志，精确定位问题。status: success/failed/skipped/error。
-- ============================================================
CREATE TABLE IF NOT EXISTS t_ingestion_task_node (
    id          BIGSERIAL    PRIMARY KEY,
    task_id     VARCHAR(64)  NOT NULL,                            -- 任务 id
    node_id     VARCHAR(64)  NOT NULL,                            -- 节点 id
    status      VARCHAR(16)  NOT NULL DEFAULT '',                 -- success/failed/skipped/error
    message     TEXT,                                            -- 日志消息
    duration_ms BIGINT,                                          -- 耗时（毫秒）
    created_at  TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  t_ingestion_task_node IS '入库任务节点日志表';
COMMENT ON COLUMN t_ingestion_task_node.status IS 'success/failed/skipped/error';
CREATE INDEX idx_task_node_tid ON t_ingestion_task_node (task_id);

-- ============================================================
-- RAG 会话消息表（移植自 sparkxV2 t_conversation_message）
--   实体见 ConversationMessageEntity.java。滑动窗口策略：每会话保留最近 historyKeepTurns*2 条。
-- ============================================================
CREATE TABLE IF NOT EXISTS t_conversation_message (
    id               BIGSERIAL    PRIMARY KEY,
    conversation_id  VARCHAR(64)  NOT NULL,                       -- 会话 id
    user_id          VARCHAR(64),                                 -- 用户 id
    role             VARCHAR(16)  NOT NULL DEFAULT '',            -- 角色：user / assistant / system
    content          TEXT,                                        -- 消息内容
    thinking_content TEXT,                                        -- 思考过程内容（reasoning，部分模型支持），可为 NULL
    created_at       TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  t_conversation_message IS 'RAG 会话消息表';
COMMENT ON COLUMN t_conversation_message.role            IS '角色：user / assistant / system';
COMMENT ON COLUMN t_conversation_message.thinking_content IS '思考过程内容';
CREATE INDEX idx_conv_msg_cid ON t_conversation_message (conversation_id);

-- ============================================================
-- RAG 会话话题摘要表（移植自 sparkxV2 t_conversation_summary）
--   实体见 ConversationSummaryEntity.java。每会话一份摘要（conversationId 为主键）。
--   ★ 核心设计——"只记话题不记答案"：摘要仅作历史讨论的话题索引，不含具体答案。
-- ============================================================
CREATE TABLE IF NOT EXISTS t_conversation_summary (
    conversation_id  VARCHAR(64)  PRIMARY KEY,                    -- 会话 id（主键）
    user_id          VARCHAR(64),                                 -- 用户 id
    summary          TEXT,                                        -- 话题摘要（只记话题不记答案）
    last_message_id  BIGINT,                                      -- 增量压缩下界：已摘要的最后一条消息 id
    updated_at       TIMESTAMP    DEFAULT now()
);
COMMENT ON TABLE  t_conversation_summary IS 'RAG 会话话题摘要表';
COMMENT ON COLUMN t_conversation_summary.last_message_id IS '增量压缩下界：已摘要的最后一条消息 id';

-- 4) 补建「知识图谱」菜单（挂在知识库目录 pid=100 下，与样例查询并列）
--    若已存在则跳过。id=190 主菜单 + 191~198 功能按钮。
INSERT INTO admin_menu (id, pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT 190, 100, '知识图谱', 1, 'knowledgegraph', 'knowledge-graph', '/knowledge/graph/index', 'knowledge/graph/index', '', 50, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE id = 190);

INSERT INTO admin_menu (id, pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    (191, 190, '配置',     2, '', '', '', 'knowledge/graph/config',           '', 95, 1, now(), now()),
    (192, 190, '知识库开关', 2, '', '', '', 'knowledge/graph/kbSetting',       '', 90, 1, now(), now()),
    (193, 190, '触发抽取', 2, '', '', '', 'knowledge/graph/extract',          '', 85, 1, now(), now()),
    (194, 190, '抽取进度', 2, '', '', '', 'knowledge/graph/extract/progress', '', 80, 1, now(), now()),
    (195, 190, '抽取记录', 2, '', '', '', 'knowledge/graph/records',          '', 75, 1, now(), now()),
    (196, 190, '检索测试', 2, '', '', '', 'knowledge/graph/hitTest',          '', 70, 1, now(), now()),
    (197, 190, '图谱可视化', 2, '', '', '', 'knowledge/graph/visualization',  '', 65, 1, now(), now()),
    (198, 190, '删除',     2, '', '', '', 'knowledge/graph/delete',           '', 60, 1, now(), now())
ON CONFLICT (id) DO NOTHING;

SELECT setval('admin_menu_id_seq', (SELECT MAX(id) FROM admin_menu));

-- ============================================================
-- 工单模块菜单（顶级目录 + 二级菜单 + 功能按钮）
--   放在所有硬编码 id 之后执行：此时 1~198 已全部入库，序列已推到 MAX(id)=198，
--   工单自增 id 将从 199 起连续分配，与任何硬编码段都不冲突。
--   父子关系通过 flag 子查询定位父节点（工单各 flag 在 type=1 菜单中全局唯一）。
--   component 对应 admin/src/views/workOrder/xxx
--   auth 对应后端接口，权限白名单精确匹配
-- ============================================================

-- 一级目录
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    (0, '工单客服', 1, 'workOrder', '#', 'LAYOUT', '#', 'CustomerServiceOutlined', 60, 1, now(), now());

-- 二级菜单（pid 指向"工单客服"目录）
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='workOrder'), '工单列表', 1, 'ticket',  'ticket',  '/workOrder/index',          'workOrder/index',           '', 95, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='workOrder'), '工单分类', 1, 'cate',    'cate',    '/workOrder/category/index', 'workOrder/category/index',  '', 85, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='workOrder'), '联系人',   1, 'contact', 'contact', '/workOrder/contact/index',  'workOrder/contact/index',   '', 80, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='workOrder'), 'SLA策略',  1, 'sla',     'sla',     '/workOrder/sla/index',      'workOrder/sla/index',       '', 75, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='workOrder'), '快捷回复', 1, 'reply',   'reply',   '/workOrder/quickReply/index','workOrder/quickReply/index','', 70, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='workOrder'), '工单评价', 1, 'evaluate','evaluate','/workOrder/evaluate/index', 'workOrder/evaluate/index',  '', 65, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='workOrder'), '回访记录', 1, 'callback','callback','/workOrder/callback/index', 'workOrder/callback/index',  '', 60, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='workOrder'), '工单统计', 1, 'stats',   'stats',   '/workOrder/stats/index',    'workOrder/stats/index',     '', 55, 1, now(), now());

-- 工单列表 功能按钮(type=2)
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '创建工单', 2, '', '', '', 'workOrder/create',         '', 95, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '工单详情', 2, '', '', '', 'workOrder/detail',         '', 90, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '认领',     2, '', '', '', 'workOrder/claim',          '', 85, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '分配',     2, '', '', '', 'workOrder/assign',         '', 80, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '转接',     2, '', '', '', 'workOrder/transfer',       '', 75, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '对外回复', 2, '', '', '', 'workOrder/reply',          '', 70, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '内部备注', 2, '', '', '', 'workOrder/note',           '', 65, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '标记解决', 2, '', '', '', 'workOrder/solve',          '', 60, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '重开',     2, '', '', '', 'workOrder/reopen',         '', 55, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '取消',     2, '', '', '', 'workOrder/cancel',         '', 50, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '关闭',     2, '', '', '', 'workOrder/close',          '', 45, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '升级',     2, '', '', '', 'workOrder/upgrade',        '', 40, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '工单流水', 2, '', '', '', 'workOrder/logs',           '', 35, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '上传附件', 2, '', '', '', 'workOrder/upload',         '', 30, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '待办工单', 2, '', '', '', 'workOrder/myTickets',      '', 25, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '工单池',   2, '', '', '', 'workOrder/pool',           '', 20, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '服务分类', 2, '', '', '', 'workOrder/serviceCateList','', 15, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='ticket'), '地区列表', 2, '', '', '', 'workOrder/areaList',       '', 10, 1, now(), now());

-- 工单分类 功能按钮
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='cate'), '分类树',   2, '', '', '', 'workOrder/category/tree', '', 90, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='cate'), '添加分类', 2, '', '', '', 'workOrder/category/add',  '', 85, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='cate'), '编辑分类', 2, '', '', '', 'workOrder/category/edit', '', 80, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='cate'), '删除分类', 2, '', '', '', 'workOrder/category/del',  '', 75, 1, now(), now());

-- 联系人 功能按钮
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='contact'), '添加联系人', 2, '', '', '', 'workOrder/contact/add',  '', 90, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='contact'), '编辑联系人', 2, '', '', '', 'workOrder/contact/edit', '', 85, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='contact'), '删除联系人', 2, '', '', '', 'workOrder/contact/del',  '', 80, 1, now(), now());

-- SLA 功能按钮
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='sla'), '添加SLA', 2, '', '', '', 'workOrder/sla/add',  '', 90, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='sla'), '编辑SLA', 2, '', '', '', 'workOrder/sla/edit', '', 85, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='sla'), '删除SLA', 2, '', '', '', 'workOrder/sla/del',  '', 80, 1, now(), now());

-- 快捷回复 功能按钮
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='reply'), '添加回复', 2, '', '', '', 'workOrder/quickReply/add',  '', 90, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='reply'), '编辑回复', 2, '', '', '', 'workOrder/quickReply/edit', '', 85, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='reply'), '删除回复', 2, '', '', '', 'workOrder/quickReply/del',  '', 80, 1, now(), now());

-- 评价 功能按钮
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='evaluate'), '评价详情', 2, '', '', '', 'workOrder/evaluate/detail', '', 90, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='evaluate'), '提交评价', 2, '', '', '', 'workOrder/evaluate/submit', '', 85, 1, now(), now());

-- 回访 功能按钮
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='callback'), '回访详情', 2, '', '', '', 'workOrder/callback/detail', '', 90, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='callback'), '添加回访', 2, '', '', '', 'workOrder/callback/add',    '', 85, 1, now(), now());

-- 统计 功能按钮
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time) VALUES
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='stats'), '工作量',   2, '', '', '', 'workOrder/stats/workload', '', 90, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='stats'), '趋势',     2, '', '', '', 'workOrder/stats/trend',    '', 85, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='stats'), '分类统计', 2, '', '', '', 'workOrder/stats/category', '', 80, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='stats'), 'SLA时效',  2, '', '', '', 'workOrder/stats/sla',      '', 75, 1, now(), now()),
    ((SELECT id FROM admin_menu WHERE type=1 AND flag='stats'), '总览',     2, '', '', '', 'workOrder/stats/overview', '', 70, 1, now(), now());

-- 工单菜单入库后再次重置序列，确保后续自增不会回撞
SELECT setval('admin_menu_id_seq', (SELECT MAX(id) FROM admin_menu));

-- 知识库 / RAG / IM 渠道等新增自增表的序列重置（与前面模块的 setval 段对齐）
SELECT setval('ai_model_id_seq',                    COALESCE((SELECT MAX(id) FROM ai_model), 1));
SELECT setval('im_embed_channels_id_seq',           COALESCE((SELECT MAX(id) FROM im_embed_channels), 1));
SELECT setval('knowledge_question_id_seq',          COALESCE((SELECT MAX(id) FROM knowledge_question), 1));
SELECT setval('t_ingestion_pipeline_node_id_seq',   COALESCE((SELECT MAX(id) FROM t_ingestion_pipeline_node), 1));
SELECT setval('t_ingestion_task_node_id_seq',       COALESCE((SELECT MAX(id) FROM t_ingestion_task_node), 1));
SELECT setval('t_conversation_message_id_seq',      COALESCE((SELECT MAX(id) FROM t_conversation_message), 1));

-- ============================================================
-- MCP 服务管理（Model Context Protocol 客户端）
--   对齐 WeKnora 架构：作为 MCP 客户端连接外部 MCP Server（SSE / HTTP Streamable），
--   把外部工具注入知识库 RAG 管线（意图树 MCP 节点 → 工具调用 → GenerateStage）。
--   mcp_server  服务配置（url / 传输类型 / 认证 / 自定义头 / 高级配置）
--   mcp_tool    工具快照（listTools 拉取后落库，供意图树下拉选择；full_id 作为意图节点 mcpToolId 的值）
-- ============================================================
CREATE TABLE IF NOT EXISTS mcp_server (
    id              SERIAL       PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,                       -- 服务名称（如「天气查询服务」）
    description     VARCHAR(500),                                -- 描述
    enabled         BOOLEAN      NOT NULL DEFAULT true,          -- 启停
    transport_type  VARCHAR(20)  NOT NULL,                       -- 传输类型：sse / http_streamable
    url             VARCHAR(512) NOT NULL,                       -- SSE / HTTP Streamable 端点
    auth_type       VARCHAR(20)  NOT NULL DEFAULT 'none',        -- 认证类型：none / api_key / bearer
    auth_config     TEXT,                                        -- 认证 JSON（apiKey / apiKeyHeader / token）
    headers         TEXT,                                        -- 自定义请求头 JSON {"k":"v"}
    timeout_sec     INTEGER      NOT NULL DEFAULT 30,            -- 连接/调用超时（秒）
    retry_count     INTEGER      NOT NULL DEFAULT 1,             -- 失败重试次数
    remark          VARCHAR(255),                                -- 备注
    sort            INTEGER      NOT NULL DEFAULT 100,           -- 排序（数值小者靠前）
    create_time     TIMESTAMP    DEFAULT now(),
    update_time     TIMESTAMP    DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mcp_server_enabled ON mcp_server(enabled);
COMMENT ON TABLE  mcp_server IS 'MCP 服务配置表（外部 MCP Server 连接配置）';
COMMENT ON COLUMN mcp_server.transport_type IS '传输类型：sse / http_streamable（底层统一用 StreamableHttpMcpTransport，sse 仅作向后兼容标签）';
COMMENT ON COLUMN mcp_server.auth_type     IS '认证类型：none 无 / api_key 注入自定义头 / bearer 注入 Authorization: Bearer';
COMMENT ON COLUMN mcp_server.auth_config   IS '认证 JSON：{"apiKey":"...","apiKeyHeader":"X-API-Key"} 或 {"token":"..."}';
COMMENT ON COLUMN mcp_server.headers       IS '自定义请求头 JSON {"k":"v"}，与认证头叠加（自定义头优先级更高）';

CREATE TABLE IF NOT EXISTS mcp_tool (
    id              SERIAL       PRIMARY KEY,
    server_id       INTEGER      NOT NULL REFERENCES mcp_server(id) ON DELETE CASCADE, -- 关联 mcp_server.id
    full_id         VARCHAR(160) NOT NULL UNIQUE,                -- 全局唯一工具标识 "svc{serverId}__{toolName}"，= 意图节点 mcpToolId 的值
    tool_name       VARCHAR(128) NOT NULL,                       -- MCP Server 原始工具名
    description     VARCHAR(500),                                -- 工具描述
    input_schema    TEXT,                                        -- 工具入参 JSON Schema 原文
    last_synced_at  TIMESTAMP,                                   -- 最近一次同步时间
    create_time     TIMESTAMP    DEFAULT now(),
    update_time     TIMESTAMP    DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mcp_tool_server ON mcp_tool(server_id);
COMMENT ON TABLE  mcp_tool  IS 'MCP 工具快照表（listTools 拉取后落库，供意图树下拉选择）';
COMMENT ON COLUMN mcp_tool.full_id   IS '全局唯一工具标识，格式 svc{serverId}__{toolName}，与意图节点 t_intent_node.mcp_tool_id 对齐';
COMMENT ON COLUMN mcp_tool.tool_name IS 'MCP Server 暴露的原始工具名（callTool 时用这个名字）';

SELECT setval('mcp_server_id_seq', COALESCE((SELECT MAX(id) FROM mcp_server), 1));
SELECT setval('mcp_tool_id_seq',   COALESCE((SELECT MAX(id) FROM mcp_tool), 1));

-- 5) 补建「MCP服务」菜单（挂在知识库目录 pid=100 下，与知识图谱/样例查询并列）
--    ⚠️ 不写死 id：工单模块等已占用 200+ id 段，硬编码会冲突。
--    主菜单按 (pid=100 AND flag='mcp') 判重；功能按钮按 (pid=父菜单id AND auth=接口路径) 判重，
--    pid 用子查询定位父菜单（与工单模块菜单写法一致）。
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT 100, 'MCP服务', 1, 'mcp', 'mcp', '/ai/mcp/index', 'ai/mcp/index', '', 62, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = 100 AND flag = 'mcp');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp'), '服务列表', 2, '', '', '', 'ai/mcp/list', '', 95, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp') AND auth = 'ai/mcp/list');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp'), '新增服务', 2, '', '', '', 'ai/mcp/add', '', 90, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp') AND auth = 'ai/mcp/add');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp'), '编辑服务', 2, '', '', '', 'ai/mcp/edit', '', 85, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp') AND auth = 'ai/mcp/edit');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp'), '删除服务', 2, '', '', '', 'ai/mcp/del', '', 80, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp') AND auth = 'ai/mcp/del');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp'), '启用禁用', 2, '', '', '', 'ai/mcp/status', '', 75, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp') AND auth = 'ai/mcp/status');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp'), '测试连接', 2, '', '', '', 'ai/mcp/test', '', 70, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp') AND auth = 'ai/mcp/test');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp'), '刷新工具', 2, '', '', '', 'ai/mcp/refresh', '', 65, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'mcp') AND auth = 'ai/mcp/refresh');

-- ============================================================
-- 知识库智能体表（knowledge_agent）
--   智能体 = 一套可保存的 RAG 参数 + SSE 测试对话 + LLM-as-judge 评估。
--   字段拆分存表（对齐 knowledge_base 风格），便于 SQL 查询/排序/展示。
--   实体见 KnowledgeAgent.java。关联知识库为多个（knowledge_base_ids 逗号分隔）。
--   ★ 配置仅作智能体测试链路运行时覆盖源；IM 客服链路不读此表（行为不变）。
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_agent (
    id                  VARCHAR(64)  PRIMARY KEY,                   -- UUID hex，业务生成
    name                VARCHAR(128) NOT NULL DEFAULT '',           -- 智能体名称
    description         TEXT,                                      -- 描述
    avatar              VARCHAR(64),                               -- 头像 emoji
    kb_mode             VARCHAR(20)  NOT NULL DEFAULT 'selected',   -- 知识库模式 all全部/selected指定/none不使用
    knowledge_base_ids  VARCHAR(1024) NOT NULL DEFAULT '',          -- 关联知识库 id，逗号分隔（kb_mode=selected 时生效）
    document_ids        VARCHAR(2048) NOT NULL DEFAULT '',          -- 限定文档 id，逗号分隔（kb_mode=selected 时可选，空=整库）
    chat_model_id       INTEGER,                                   -- 对话模型 ai_model.id，空走默认模型
    chat_model_name     VARCHAR(128),                              -- 冗余：对话模型显示名（展示用）
    system_prompt       TEXT,                                      -- 自定义系统提示词（空走场景模板）
    temperature         DOUBLE PRECISION NOT NULL DEFAULT 0.3,     -- 温度（0~2）
    max_tokens          INTEGER NOT NULL DEFAULT 2048,             -- 最大生成 token
    history_turns       INTEGER NOT NULL DEFAULT 4,                -- 上下文记忆轮数
    embedding_top_k     INTEGER NOT NULL DEFAULT 10,               -- 向量召回 topK
    vector_threshold    DOUBLE PRECISION NOT NULL DEFAULT 0.2,     -- 向量相似度阈值
    keyword_threshold   DOUBLE PRECISION NOT NULL DEFAULT 0.3,     -- 关键词阈值
    rerank_model_id     INTEGER,                                   -- 重排模型 ai_model.id（type=3），空用全局默认
    rerank_model_name   VARCHAR(128),                              -- 冗余：重排模型显示名（展示用）
    rerank_enabled      SMALLINT NOT NULL DEFAULT 1,               -- 是否启用重排 1启用 2禁用
    rerank_top_k        INTEGER NOT NULL DEFAULT 5,                -- 重排 topK
    rerank_threshold    DOUBLE PRECISION NOT NULL DEFAULT 0.3,     -- 重排阈值
    fallback_strategy   VARCHAR(20) NOT NULL DEFAULT 'model',      -- 兜底策略 model/fixed
    fallback_response   TEXT,                                      -- 兜底固定话术（strategy=fixed 时生效）
    welcome             TEXT,                                      -- 开场白
    suggested_questions TEXT,                                      -- 推荐问题 JSON 数组（["问题1","问题2"]）
    status              SMALLINT NOT NULL DEFAULT 1,               -- 1正常 2禁用
    created_at          TIMESTAMP DEFAULT now(),
    updated_at          TIMESTAMP DEFAULT now()
);
COMMENT ON TABLE  knowledge_agent IS '知识库智能体表';
COMMENT ON COLUMN knowledge_agent.id                   IS '主键 UUID hex';
COMMENT ON COLUMN knowledge_agent.name                 IS '智能体名称';
COMMENT ON COLUMN knowledge_agent.description          IS '描述';
COMMENT ON COLUMN knowledge_agent.avatar               IS '头像 emoji';
COMMENT ON COLUMN knowledge_agent.kb_mode              IS '知识库模式 all全部/selected指定/none不使用';
COMMENT ON COLUMN knowledge_agent.knowledge_base_ids   IS '关联知识库 id，逗号分隔（kb_mode=selected 时生效）';
COMMENT ON COLUMN knowledge_agent.document_ids         IS '限定文档 id，逗号分隔（kb_mode=selected 时可选，空=整库）';
COMMENT ON COLUMN knowledge_agent.chat_model_id        IS '对话模型 ai_model.id，空走默认模型';
COMMENT ON COLUMN knowledge_agent.chat_model_name      IS '冗余：对话模型显示名';
COMMENT ON COLUMN knowledge_agent.system_prompt        IS '自定义系统提示词（空走场景模板）';
COMMENT ON COLUMN knowledge_agent.temperature          IS '温度（0~2）';
COMMENT ON COLUMN knowledge_agent.max_tokens           IS '最大生成 token';
COMMENT ON COLUMN knowledge_agent.history_turns        IS '上下文记忆轮数';
COMMENT ON COLUMN knowledge_agent.embedding_top_k      IS '向量召回 topK';
COMMENT ON COLUMN knowledge_agent.vector_threshold     IS '向量相似度阈值';
COMMENT ON COLUMN knowledge_agent.keyword_threshold    IS '关键词阈值';
COMMENT ON COLUMN knowledge_agent.rerank_model_id      IS '重排模型 ai_model.id（type=3），空用全局默认';
COMMENT ON COLUMN knowledge_agent.rerank_model_name    IS '冗余：重排模型显示名';
COMMENT ON COLUMN knowledge_agent.rerank_enabled       IS '是否启用重排 1启用 2禁用';
COMMENT ON COLUMN knowledge_agent.rerank_top_k         IS '重排 topK';
COMMENT ON COLUMN knowledge_agent.rerank_threshold     IS '重排阈值';
COMMENT ON COLUMN knowledge_agent.fallback_strategy    IS '兜底策略 model/fixed';
COMMENT ON COLUMN knowledge_agent.fallback_response    IS '兜底固定话术（strategy=fixed 时生效）';
COMMENT ON COLUMN knowledge_agent.welcome              IS '开场白';
COMMENT ON COLUMN knowledge_agent.suggested_questions  IS '推荐问题 JSON 数组';
COMMENT ON COLUMN knowledge_agent.status               IS '1正常 2禁用';
COMMENT ON COLUMN knowledge_agent.created_at           IS '创建时间';
COMMENT ON COLUMN knowledge_agent.updated_at           IS '更新时间';

-- 6) 补建「智能体」菜单（挂在知识库目录 pid=100 下，与 MCP/知识图谱并列）
--    主菜单按 (pid=100 AND flag='agent') 判重；功能按钮按 (pid=父菜单id AND auth=接口路径) 判重。
--    ★ component 必须严格等于 views/ 下文件路径（generator.ts dynamicImport 精确匹配）：
--      前端文件在 admin/src/views/agent/index.vue → component='/agent/index'。
INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT 100, '智能体', 1, 'agent', 'agent', '/agent/index', 'agent/index', '', 60, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = 100 AND flag = 'agent');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent'), '智能体列表', 2, '', '', '', 'knowledge/agent/list', '', 95, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent') AND auth = 'knowledge/agent/list');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent'), '新增智能体', 2, '', '', '', 'knowledge/agent/add', '', 90, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent') AND auth = 'knowledge/agent/add');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent'), '编辑智能体', 2, '', '', '', 'knowledge/agent/edit', '', 85, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent') AND auth = 'knowledge/agent/edit');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent'), '删除智能体', 2, '', '', '', 'knowledge/agent/del', '', 80, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent') AND auth = 'knowledge/agent/del');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent'), '测试对话', 2, '', '', '', 'knowledge/agent/chat', '', 75, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent') AND auth = 'knowledge/agent/chat');

INSERT INTO admin_menu (pid, name, type, flag, path, component, auth, icon, sort, status, create_time, update_time)
SELECT (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent'), '评估报告', 2, '', '', '', 'knowledge/agent/eval', '', 70, 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE pid = (SELECT id FROM admin_menu WHERE pid = 100 AND flag = 'agent') AND auth = 'knowledge/agent/eval');

SELECT setval('admin_menu_id_seq', (SELECT MAX(id) FROM admin_menu));

-- ============================================================
-- knowledge_agent 补充重排模型字段（rerank_model_id / rerank_model_name）
--   智能体可选指定重排模型（ai_model.id, type=3）；空则用全局默认 ScoringModel。
--   ALTER ADD COLUMN IF NOT EXISTS 幂等，已建表环境补字段用。
-- ============================================================
ALTER TABLE knowledge_agent ADD COLUMN IF NOT EXISTS rerank_model_id   INTEGER;
ALTER TABLE knowledge_agent ADD COLUMN IF NOT EXISTS rerank_model_name VARCHAR(128);
COMMENT ON COLUMN knowledge_agent.rerank_model_id   IS '重排模型 ai_model.id（type=3），空用全局默认';
COMMENT ON COLUMN knowledge_agent.rerank_model_name IS '冗余：重排模型显示名';

-- ============================================================
-- knowledge_agent 补充知识库三态字段（kb_mode / document_ids）
--   对齐 WeKnora：all 全部 / selected 指定 / none 不使用；selected 可选文档级限定。
-- ============================================================
ALTER TABLE knowledge_agent ADD COLUMN IF NOT EXISTS kb_mode      VARCHAR(20) NOT NULL DEFAULT 'selected';
ALTER TABLE knowledge_agent ADD COLUMN IF NOT EXISTS document_ids VARCHAR(2048) NOT NULL DEFAULT '';
COMMENT ON COLUMN knowledge_agent.kb_mode      IS '知识库模式 all全部/selected指定/none不使用';
COMMENT ON COLUMN knowledge_agent.document_ids IS '限定文档 id，逗号分隔（kb_mode=selected 时可选，空=整库）';

-- knowledge_agent 补充意图/改写专用模型字段（rewrite_model_id / rewrite_model_name）
--   智能体可选指定一个对话类小快模型（ai_model.id, type=1）用于意图分类与查询改写，
--   降本提速（主回答仍用大模型）；空则走全局默认对话候选链。借鉴 WeKnora QueryUnderstandModelID。
ALTER TABLE knowledge_agent ADD COLUMN IF NOT EXISTS rewrite_model_id   INTEGER;
ALTER TABLE knowledge_agent ADD COLUMN IF NOT EXISTS rewrite_model_name VARCHAR(128);
COMMENT ON COLUMN knowledge_agent.rewrite_model_id   IS '意图/改写专用模型 ai_model.id（type=1），空用全局默认大模型';
COMMENT ON COLUMN knowledge_agent.rewrite_model_name IS '冗余：意图/改写专用模型显示名';