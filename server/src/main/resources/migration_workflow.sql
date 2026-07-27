-- ============================================================
-- 编排（Workflow）模块建表迁移脚本
-- 数据库：PostgreSQL（schema: public）
-- 说明：编排模块为全新功能，3 张表均为新增，与历史数据无关联。
--       本脚本幂等：用 IF NOT EXISTS 保护，可对已初始化的库重复执行。
--       全量 DDL 也已追加在 sparkx.sql 末尾，新库直接跑 sparkx.sql 即可包含本模块。
-- 执行方式：psql -d <库名> -f migration_workflow.sql
--          或在 IDEA/Navicat 里整段执行。
-- ============================================================

-- ----------------------------
-- 1. 编排流程主表
--    一条记录 = 一个可视化编排流程。flow_data 存 X6 graph.toJSON() 的 JSON。
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."workflow" (
  "id"          varchar(32)  COLLATE "pg_catalog"."default" NOT NULL,
  "name"        varchar(100) COLLATE "pg_catalog"."default",
  "description" varchar(500) COLLATE "pg_catalog"."default",
  "flow_data"   text         COLLATE "pg_catalog"."default",
  "status"      int2         DEFAULT 1,
  "created_at"  timestamp(6),
  "updated_at"  timestamp(6)
);
COMMENT ON COLUMN "public"."workflow"."id"          IS '主键 UUID hex（业务生成）';
COMMENT ON COLUMN "public"."workflow"."name"        IS '编排名称';
COMMENT ON COLUMN "public"."workflow"."description" IS '描述';
COMMENT ON COLUMN "public"."workflow"."flow_data"   IS '流程设计 JSON（X6 graph.toJSON()）';
COMMENT ON COLUMN "public"."workflow"."status"      IS '1正常 2禁用';
COMMENT ON COLUMN "public"."workflow"."created_at"  IS '创建时间';
COMMENT ON COLUMN "public"."workflow"."updated_at"  IS '更新时间';
COMMENT ON TABLE  "public"."workflow"               IS '编排流程表';

-- 主键（IF NOT EXISTS：PG 14+ 支持；若库版本较低且报错，把此句改成先判断再 ADD）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'workflow_pkey'
  ) THEN
    ALTER TABLE "public"."workflow" ADD CONSTRAINT "workflow_pkey" PRIMARY KEY ("id");
  END IF;
END$$;

-- ----------------------------
-- 2. 编排流程运行时表
--    一次调试对话 = 一行。
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."workflow_runtime" (
  "id"          int8 NOT NULL,
  "workflow_id" varchar(32)  COLLATE "pg_catalog"."default",
  "user_id"     varchar(64)  COLLATE "pg_catalog"."default",
  "title"       varchar(255) COLLATE "pg_catalog"."default",
  "created_at"  timestamp(6),
  "updated_at"  timestamp(6)
);
COMMENT ON COLUMN "public"."workflow_runtime"."id"          IS '主键（自增）';
COMMENT ON COLUMN "public"."workflow_runtime"."workflow_id" IS '编排流程 id';
COMMENT ON COLUMN "public"."workflow_runtime"."user_id"     IS '用户 id';
COMMENT ON COLUMN "public"."workflow_runtime"."title"       IS '首问截断（取首条问题前 255 字）';
COMMENT ON COLUMN "public"."workflow_runtime"."created_at"  IS '创建时间';
COMMENT ON COLUMN "public"."workflow_runtime"."updated_at"  IS '更新时间';
COMMENT ON TABLE  "public"."workflow_runtime"               IS '编排流程运行时表';

-- 自增序列 + 主键
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'workflow_runtime_pkey'
  ) THEN
    ALTER TABLE "public"."workflow_runtime" ADD CONSTRAINT "workflow_runtime_pkey" PRIMARY KEY ("id");
  END IF;
END$$;
-- id 用自增（与 sparkx.sql 的 IdType.AUTO 对齐）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_sequences WHERE sequencename = 'workflow_runtime_id_seq'
  ) THEN
    CREATE SEQUENCE "public"."workflow_runtime_id_seq" NO MINVALUE NO MAXVALUE START WITH 1 INCREMENT BY 1;
    ALTER TABLE "public"."workflow_runtime" ALTER COLUMN "id" SET DEFAULT nextval('public.workflow_runtime_id_seq');
    ALTER SEQUENCE "public"."workflow_runtime_id_seq" OWNED BY "public"."workflow_runtime"."id";
  END IF;
END$$;

-- ----------------------------
-- 3. 编排流程运行时上下文表
--    每个节点执行 = 一行，按 step 排序即执行顺序。
--    output_data/model_data 为 JSON 文本：
--      - 全局变量 sys.question/sys.time/sys.sessionId/sys.workflowId/sys.ip 平铺
--      - 节点产出（sys.content/sys.result/sys.purposeName/sys.agentContent/sys.answer/datasets.*/switch.result）
--        按「来源 cell」分区写在 node.<cellId> 子对象下（Q3：避免并行分支同名输出互相覆盖）
-- ----------------------------
CREATE TABLE IF NOT EXISTS "public"."workflow_runtime_context" (
  "id"          int8 NOT NULL,
  "runtime_id"  int8,
  "node_type"   varchar(55)  COLLATE "pg_catalog"."default",
  "step"        int2,
  "output_data" text         COLLATE "pg_catalog"."default",
  "model_data"  text         COLLATE "pg_catalog"."default",
  "cell"        varchar(155) COLLATE "pg_catalog"."default",
  "created_at"  timestamp(6),
  "updated_at"  timestamp(6)
);
COMMENT ON COLUMN "public"."workflow_runtime_context"."id"          IS '主键（自增）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."runtime_id"  IS '运行时 id（workflow_runtime.id）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."node_type"   IS '节点类型（start-node/llm-node/answer-node/...）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."step"        IS '步骤号（按执行顺序自增）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."output_data" IS '出参数据 JSON（sys.* 全局 + node.<cell> 分区产出）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."model_data"  IS '模型数据 JSON（节点配置 + token 用量）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."cell"        IS 'X6 节点 id';
COMMENT ON COLUMN "public"."workflow_runtime_context"."created_at"  IS '创建时间';
COMMENT ON COLUMN "public"."workflow_runtime_context"."updated_at"  IS '更新时间';
COMMENT ON TABLE  "public"."workflow_runtime_context"               IS '编排流程运行时上下文表';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'workflow_runtime_context_pkey'
  ) THEN
    ALTER TABLE "public"."workflow_runtime_context" ADD CONSTRAINT "workflow_runtime_context_pkey" PRIMARY KEY ("id");
  END IF;
END$$;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_sequences WHERE sequencename = 'workflow_runtime_context_id_seq'
  ) THEN
    CREATE SEQUENCE "public"."workflow_runtime_context_id_seq" NO MINVALUE NO MAXVALUE START WITH 1 INCREMENT BY 1;
    ALTER TABLE "public"."workflow_runtime_context" ALTER COLUMN "id" SET DEFAULT nextval('public.workflow_runtime_context_id_seq');
    ALTER SEQUENCE "public"."workflow_runtime_context_id_seq" OWNED BY "public"."workflow_runtime_context"."id";
  END IF;
END$$;

-- ----------------------------
-- 索引（按 workflow_id / runtime_id 查询是高频路径）
-- ----------------------------
CREATE INDEX IF NOT EXISTS "idx_workflow_runtime_workflow_id"
    ON "public"."workflow_runtime" ("workflow_id");
CREATE INDEX IF NOT EXISTS "idx_workflow_runtime_context_runtime_id"
    ON "public"."workflow_runtime_context" ("runtime_id");
