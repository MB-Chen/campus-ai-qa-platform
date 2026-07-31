/*
 Navicat Premium Dump SQL

 Source Server         : xservice的pgsql
 Source Server Type    : PostgreSQL
 Source Server Version : 150013 (150013)
 Source Host           : localhost:5432
 Source Catalog        : sparkx
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 150013 (150013)
 File Encoding         : 65001

 Date: 30/07/2026 22:31:07
*/


-- ----------------------------
-- pgvector 扩展：halfvec/sparsevec/vector 类型及底层 C 函数由该扩展提供
CREATE EXTENSION IF NOT EXISTS vector;

-- Sequence structure for admin_user_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."admin_user_id_seq";
CREATE SEQUENCE "public"."admin_user_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for ai_model_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."ai_model_id_seq";
CREATE SEQUENCE "public"."ai_model_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for ext_service_config_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."ext_service_config_id_seq";
CREATE SEQUENCE "public"."ext_service_config_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for kg_config_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."kg_config_id_seq";
CREATE SEQUENCE "public"."kg_config_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for kg_entity_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."kg_entity_id_seq";
CREATE SEQUENCE "public"."kg_entity_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for kg_extraction_record_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."kg_extraction_record_id_seq";
CREATE SEQUENCE "public"."kg_extraction_record_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for knowledge_question_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."knowledge_question_id_seq";
CREATE SEQUENCE "public"."knowledge_question_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for mcp_server_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."mcp_server_id_seq";
CREATE SEQUENCE "public"."mcp_server_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for mcp_tool_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."mcp_tool_id_seq";
CREATE SEQUENCE "public"."mcp_tool_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for sample_query_config_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sample_query_config_id_seq";
CREATE SEQUENCE "public"."sample_query_config_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for sample_query_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."sample_query_id_seq";
CREATE SEQUENCE "public"."sample_query_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for t_conversation_message_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."t_conversation_message_id_seq";
CREATE SEQUENCE "public"."t_conversation_message_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for t_ingestion_pipeline_node_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."t_ingestion_pipeline_node_id_seq";
CREATE SEQUENCE "public"."t_ingestion_pipeline_node_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for t_ingestion_task_node_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."t_ingestion_task_node_id_seq";
CREATE SEQUENCE "public"."t_ingestion_task_node_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for workflow_runtime_context_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."workflow_runtime_context_id_seq";
CREATE SEQUENCE "public"."workflow_runtime_context_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for workflow_runtime_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."workflow_runtime_id_seq";
CREATE SEQUENCE "public"."workflow_runtime_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Table structure for admin_user
-- ----------------------------
DROP TABLE IF EXISTS "public"."admin_user";
CREATE TABLE "public"."admin_user" (
  "id" int4 NOT NULL DEFAULT nextval('admin_user_id_seq'::regclass),
  "account" varchar(30) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "nickname" varchar(50) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "password" varchar(64) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "salt" varchar(64) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "avatar" varchar(255) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "status" int2 NOT NULL DEFAULT 1,
  "last_login_ip" varchar(55) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "last_login_time" timestamp(6),
  "create_time" timestamp(6),
  "update_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."admin_user"."salt" IS '加密盐（每用户独立）';
COMMENT ON TABLE "public"."admin_user" IS '员工表';

-- ----------------------------
-- Records of admin_user
-- ----------------------------
INSERT INTO "public"."admin_user" VALUES (1, 'admin', '管理员', '9b94e5b87d65a9590aed658a6624d665', 'xservice-admin-salt-2026', '', 1, '', NULL, '2026-07-22 03:56:53.778705', '2026-07-22 03:56:53.778705');

-- ----------------------------
-- Table structure for ai_model
-- ----------------------------
DROP TABLE IF EXISTS "public"."ai_model";
CREATE TABLE "public"."ai_model" (
  "id" int4 NOT NULL DEFAULT nextval('ai_model_id_seq'::regclass),
  "name" varchar(100) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "type" int2 NOT NULL DEFAULT 1,
  "provider" varchar(64) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "credential" text COLLATE "pg_catalog"."default",
  "models" text COLLATE "pg_catalog"."default",
  "function_calling" text COLLATE "pg_catalog"."default",
  "options" text COLLATE "pg_catalog"."default",
  "status" int2 NOT NULL DEFAULT 1,
  "priority" int4 NOT NULL DEFAULT 100,
  "supports_thinking" int2 NOT NULL DEFAULT 0,
  "create_time" timestamp(6),
  "update_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."ai_model"."type" IS '1对话 2向量 3重排 4视觉(VLM)';
COMMENT ON COLUMN "public"."ai_model"."credential" IS '凭证 JSON 数组';
COMMENT ON COLUMN "public"."ai_model"."options" IS '选项 JSON 数组：url/temperature/maxOutputTokens 等';
COMMENT ON COLUMN "public"."ai_model"."priority" IS '候选优先级，数值小者优先';
COMMENT ON TABLE "public"."ai_model" IS 'AI 模型配置表';

-- ----------------------------
-- Records of ai_model
-- ----------------------------
INSERT INTO "public"."ai_model" VALUES (1, 'DeepSeek', 1, 'openai', '[{"field":"apiKey","value":""}]', 'deepseek-v4-flash,deepseek-v4-pro', NULL, '[{"field":"url","value":"https://api.deepseek.com/v1/chat/completions"},{"field":"temperature","value":0.1,"range":[0,2]},{"field":"maxOutputTokens","value":2048,"range":[1,128000]}]', 1, 100, 1, '2026-07-22 03:56:53', '2026-07-31 18:24:10.036183');
-- 向量模型：百度千帆 v2 embeddings（请直接填完整 /embeddings 路径，系统不再自动补全）
INSERT INTO "public"."ai_model" VALUES (2, '百度千帆', 2, 'openai', '[{"field":"apiKey","value":""}]', 'qwen3-embedding-8b', NULL, '[{"field":"url","value":"https://qianfan.baidubce.com/v2/embeddings"}]', 1, 100, 0, '2026-07-22 03:56:53', '2026-07-31 18:16:21.959191');
-- 重排模型：百度千帆 v2 rerank（请直接填完整 /rerank 路径，系统不再自动补全）
INSERT INTO "public"."ai_model" VALUES (3, '百度千帆', 3, 'openai', '[{"field":"apiKey","value":""}]', 'bce-reranker-base', NULL, '[{"field":"url","value":"https://qianfan.baidubce.com/v2/rerank"}]', 1, 100, 0, '2026-07-22 03:56:53', '2026-07-31 17:24:15.96754');
-- 视觉模型：智谱AI，url 为完整 /chat/completions 路径（多模态走 chat completions 协议）
INSERT INTO "public"."ai_model" VALUES (4, '智谱AI', 4, 'openai', '[{"field":"apiKey","value":""}]', 'glm-4.6v', NULL, '[{"field":"url","value":"https://open.bigmodel.cn/api/paas/v4/chat/completions"}]', 1, 100, 0, '2026-07-22 03:56:53', '2026-07-22 03:56:53');

-- ----------------------------
-- Table structure for chunks
-- ----------------------------
DROP TABLE IF EXISTS "public"."chunks";
CREATE TABLE "public"."chunks" (
  "id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "kb_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "content" text COLLATE "pg_catalog"."default",
  "embedding" vector,
  "tsv" tsvector,
  "metadata" jsonb,
  "created_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."chunks"."kb_id" IS '关联 knowledge_base.id';
COMMENT ON COLUMN "public"."chunks"."embedding" IS '分块向量';
COMMENT ON COLUMN "public"."chunks"."tsv" IS '全文检索向量';
COMMENT ON COLUMN "public"."chunks"."metadata" IS 'JSON 元数据';
COMMENT ON TABLE "public"."chunks" IS '知识库子块表';

-- ----------------------------
-- Records of chunks
-- ----------------------------

-- ----------------------------
-- Table structure for document
-- ----------------------------
DROP TABLE IF EXISTS "public"."document";
CREATE TABLE "public"."document" (
  "id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "kb_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "file_name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "file_size" int8,
  "storage_url" varchar(512) COLLATE "pg_catalog"."default",
  "ingestion_summary" jsonb,
  "status" varchar(20) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'pending'::character varying,
  "chunk_count" int4 NOT NULL DEFAULT 0,
  "question_status" int2 NOT NULL DEFAULT 1,
  "active" int2 NOT NULL DEFAULT 1,
  "created_at" timestamp(6) DEFAULT now(),
  "updated_at" timestamp(6) DEFAULT now(),
  "kg_enabled" int2 NOT NULL DEFAULT 2
)
;
COMMENT ON COLUMN "public"."document"."kb_id" IS '关联 knowledge_base.id';
COMMENT ON COLUMN "public"."document"."storage_url" IS 'MinIO 对象 key';
COMMENT ON COLUMN "public"."document"."ingestion_summary" IS '入库耗时统计 JSON';
COMMENT ON COLUMN "public"."document"."status" IS '向量化状态 pending|processing|done|failed';
COMMENT ON COLUMN "public"."document"."question_status" IS '问题生成状态 1待生成 2生成中 3已生成';
COMMENT ON COLUMN "public"."document"."active" IS '是否启用 1正常 2禁用';
COMMENT ON COLUMN "public"."document"."kg_enabled" IS '知识图谱开关 1=启用 2=禁用（文档级）';
COMMENT ON TABLE "public"."document" IS '知识库文档表';

-- ----------------------------
-- Records of document
-- ----------------------------

-- ----------------------------
-- Table structure for ext_service_config
-- ----------------------------
DROP TABLE IF EXISTS "public"."ext_service_config";
CREATE TABLE "public"."ext_service_config" (
  "id" int4 NOT NULL DEFAULT nextval('ext_service_config_id_seq'::regclass),
  "name" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "category" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "config" text COLLATE "pg_catalog"."default",
  "remark" varchar(255) COLLATE "pg_catalog"."default",
  "status" int2 NOT NULL DEFAULT 1,
  "sort" int4 NOT NULL DEFAULT 100,
  "create_time" timestamp(6) DEFAULT now(),
  "update_time" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."ext_service_config"."category" IS '服务类别：mineru_self 自建MinerU / mineru_cloud 云端MinerU / 未来扩展';
COMMENT ON COLUMN "public"."ext_service_config"."config" IS '配置 JSON，schema 按 category 不同（详见 IExtServiceConfigService）';
COMMENT ON TABLE "public"."ext_service_config" IS '外部服务配置表';

-- ----------------------------
-- Records of ext_service_config
-- ----------------------------
INSERT INTO "public"."ext_service_config" VALUES (1, '本地Neo4j', 'neo4j_self', '{"uri":"bolt://neo4j:7687","username":"neo4j","password":"neo4j123"}', NULL, 1, 100, '2026-07-22 12:55:09.406744', '2026-07-22 12:55:09.406744');
INSERT INTO "public"."ext_service_config" VALUES (2, '本地MinerU', 'mineru_self', '{"endpoint":"http://127.0.0.1:8000","model":"pipeline","vlmServerUrl":"","enableFormula":true,"enableTable":true,"enableOcr":true,"language":"ch","timeoutSec":1000}', NULL, 1, 100, '2026-07-22 12:55:09.406744', '2026-07-22 12:55:09.406744');

-- ----------------------------
-- Table structure for kg_config
-- ----------------------------
DROP TABLE IF EXISTS "public"."kg_config";
CREATE TABLE "public"."kg_config" (
  "id" int4 NOT NULL DEFAULT nextval('kg_config_id_seq'::regclass),
  "extract_model_id" int4,
  "extract_model_name" varchar(128) COLLATE "pg_catalog"."default",
  "embedding_model_id" int4,
  "embedding_model_name" varchar(128) COLLATE "pg_catalog"."default",
  "enabled" int2 NOT NULL DEFAULT 2,
  "similarity_threshold" numeric(4,3) NOT NULL DEFAULT 0.650,
  "extract_batch_size" int4 NOT NULL DEFAULT 5,
  "hop_depth" int2 NOT NULL DEFAULT 1,
  "second_hop_weight" numeric(3,2) NOT NULL DEFAULT 0.50,
  "entity_merge_threshold" numeric(4,3) NOT NULL DEFAULT 0.880,
  "retrieval_mode" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'local'::character varying,
  "community_enabled" int2 NOT NULL DEFAULT 2,
  "created_at" timestamp(6) DEFAULT now(),
  "updated_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."kg_config"."extract_model_id" IS '关联 ai_model.id（type=1 对话模型，用于抽实体/关系）';
COMMENT ON COLUMN "public"."kg_config"."embedding_model_id" IS '关联 ai_model.id（type=2 向量模型，用于实体向量化）';
COMMENT ON COLUMN "public"."kg_config"."enabled" IS '全局开关 1=启用 2=禁用（与 app.rag.knowledge-graph.enabled 双闸）';
COMMENT ON COLUMN "public"."kg_config"."similarity_threshold" IS '实体向量召回相似度阈值（0~1），高于此值才视为命中';
COMMENT ON COLUMN "public"."kg_config"."hop_depth" IS '子图跳数 1=一跳 2=二跳（带 second_hop_weight 衰减）';
COMMENT ON COLUMN "public"."kg_config"."entity_merge_threshold" IS '实体 embedding 合并阈值（0~1，默认 0.88）：字符串消歧之后，对实体 name+description 算余弦相似度，>= 此值合并';
COMMENT ON COLUMN "public"."kg_config"."retrieval_mode" IS '图谱检索模式 local（子图扩展）/ global（社区摘要召回）/ hybrid（双路并行）';
COMMENT ON COLUMN "public"."kg_config"."community_enabled" IS '社区检测开关 1=启用 2=禁用（global/hybrid 模式前置）';
COMMENT ON TABLE "public"."kg_config" IS '知识图谱全局配置表';

-- ----------------------------
-- Records of kg_config
-- ----------------------------
INSERT INTO "public"."kg_config" VALUES (1, 1, 'deepseek-v4-flash', 2, 'qwen3-embedding-8b', 2, 0.650, 5, 2, 0.50, 0.880, 'local', 1, '2026-07-22 03:56:56.211211', '2026-07-22 03:56:56.211211');

-- ----------------------------
-- Table structure for kg_entity
-- ----------------------------
DROP TABLE IF EXISTS "public"."kg_entity";
CREATE TABLE "public"."kg_entity" (
  "id" int8 NOT NULL DEFAULT nextval('kg_entity_id_seq'::regclass),
  "kb_id" varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
  "name" text COLLATE "pg_catalog"."default" NOT NULL,
  "canonical_name" text COLLATE "pg_catalog"."default",
  "entity_type" varchar(64) COLLATE "pg_catalog"."default",
  "description" text COLLATE "pg_catalog"."default",
  "aliases" text COLLATE "pg_catalog"."default",
  "neo4j_element_id" varchar(128) COLLATE "pg_catalog"."default",
  "source_doc_ids" text COLLATE "pg_catalog"."default",
  "source_parent_ids" text COLLATE "pg_catalog"."default",
  "embedding" vector,
  "tsv" tsvector,
  "vectorized" int2 NOT NULL DEFAULT 0,
  "status" int2 NOT NULL DEFAULT 1,
  "created_at" timestamp(6) DEFAULT now(),
  "updated_at" timestamp(6) DEFAULT now(),
  "doc_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying
)
;
COMMENT ON COLUMN "public"."kg_entity"."canonical_name" IS '消歧后规范名，作为 Neo4j 节点唯一键的一部分 (kb_id, canonical_name)';
COMMENT ON COLUMN "public"."kg_entity"."aliases" IS '别名 JSON 数组，支持同义实体召回';
COMMENT ON COLUMN "public"."kg_entity"."neo4j_element_id" IS '对应 Neo4j 节点 elementId，可视化联动用';
COMMENT ON COLUMN "public"."kg_entity"."source_parent_ids" IS '来源父块 JSON 数组（parent_chunks.id）';
COMMENT ON COLUMN "public"."kg_entity"."embedding" IS '实体向量（name+description 向量化），独立存储不写入 chunks';
COMMENT ON COLUMN "public"."kg_entity"."doc_id" IS '关联文档 id（文档级隔离键，同名实体跨文档各自独立）';
COMMENT ON TABLE "public"."kg_entity" IS '知识图谱实体向量索引表';

-- ----------------------------
-- Records of kg_entity
-- ----------------------------

-- ----------------------------
-- Table structure for kg_extraction_record
-- ----------------------------
DROP TABLE IF EXISTS "public"."kg_extraction_record";
CREATE TABLE "public"."kg_extraction_record" (
  "id" int8 NOT NULL DEFAULT nextval('kg_extraction_record_id_seq'::regclass),
  "kb_id" varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
  "document_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "status" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'pending'::character varying,
  "parent_total" int4 NOT NULL DEFAULT 0,
  "parent_done" int4 NOT NULL DEFAULT 0,
  "entity_count" int4 NOT NULL DEFAULT 0,
  "relation_count" int4 NOT NULL DEFAULT 0,
  "error_msg" text COLLATE "pg_catalog"."default",
  "started_at" timestamp(6),
  "finished_at" timestamp(6),
  "created_at" timestamp(6) DEFAULT now(),
  "updated_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."kg_extraction_record"."status" IS 'pending 待处理 / extracting 抽取中 / done 完成 / failed 失败';
COMMENT ON COLUMN "public"."kg_extraction_record"."parent_total" IS '待抽取父块总数（来自 parent_chunks by document_id）';
COMMENT ON TABLE "public"."kg_extraction_record" IS '知识图谱文档级抽取状态记录表';

-- ----------------------------
-- Records of kg_extraction_record
-- ----------------------------

-- ----------------------------
-- Table structure for knowledge_agent
-- ----------------------------
DROP TABLE IF EXISTS "public"."knowledge_agent";
CREATE TABLE "public"."knowledge_agent" (
  "id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "description" text COLLATE "pg_catalog"."default",
  "avatar" varchar(64) COLLATE "pg_catalog"."default",
  "knowledge_base_ids" varchar(1024) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "chat_model_id" int4,
  "chat_model_name" varchar(128) COLLATE "pg_catalog"."default",
  "system_prompt" text COLLATE "pg_catalog"."default",
  "temperature" float8 NOT NULL DEFAULT 0.3,
  "max_tokens" int4 NOT NULL DEFAULT 2048,
  "history_turns" int4 NOT NULL DEFAULT 4,
  "embedding_top_k" int4 NOT NULL DEFAULT 10,
  "vector_threshold" float8 NOT NULL DEFAULT 0.2,
  "keyword_threshold" float8 NOT NULL DEFAULT 0.3,
  "rerank_enabled" int2 NOT NULL DEFAULT 1,
  "rerank_top_k" int4 NOT NULL DEFAULT 5,
  "rerank_threshold" float8 NOT NULL DEFAULT 0.3,
  "fallback_strategy" varchar(20) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'model'::character varying,
  "fallback_response" text COLLATE "pg_catalog"."default",
  "welcome" text COLLATE "pg_catalog"."default",
  "suggested_questions" text COLLATE "pg_catalog"."default",
  "status" int2 NOT NULL DEFAULT 1,
  "created_at" timestamp(6) DEFAULT now(),
  "updated_at" timestamp(6) DEFAULT now(),
  "rerank_model_id" int4,
  "rerank_model_name" varchar(128) COLLATE "pg_catalog"."default",
  "kb_mode" varchar(20) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'selected'::character varying,
  "document_ids" varchar(2048) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "rewrite_model_id" int4,
  "rewrite_model_name" varchar(128) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."knowledge_agent"."id" IS '主键 UUID hex';
COMMENT ON COLUMN "public"."knowledge_agent"."name" IS '智能体名称';
COMMENT ON COLUMN "public"."knowledge_agent"."description" IS '描述';
COMMENT ON COLUMN "public"."knowledge_agent"."avatar" IS '头像 emoji';
COMMENT ON COLUMN "public"."knowledge_agent"."knowledge_base_ids" IS '关联知识库 id，逗号分隔（kb_mode=selected 时生效）';
COMMENT ON COLUMN "public"."knowledge_agent"."chat_model_id" IS '对话模型 ai_model.id，空走默认模型';
COMMENT ON COLUMN "public"."knowledge_agent"."chat_model_name" IS '冗余：对话模型显示名';
COMMENT ON COLUMN "public"."knowledge_agent"."system_prompt" IS '自定义系统提示词（空走场景模板）';
COMMENT ON COLUMN "public"."knowledge_agent"."temperature" IS '温度（0~2）';
COMMENT ON COLUMN "public"."knowledge_agent"."max_tokens" IS '最大生成 token';
COMMENT ON COLUMN "public"."knowledge_agent"."history_turns" IS '上下文记忆轮数';
COMMENT ON COLUMN "public"."knowledge_agent"."embedding_top_k" IS '向量召回 topK';
COMMENT ON COLUMN "public"."knowledge_agent"."vector_threshold" IS '向量相似度阈值';
COMMENT ON COLUMN "public"."knowledge_agent"."keyword_threshold" IS '关键词阈值';
COMMENT ON COLUMN "public"."knowledge_agent"."rerank_enabled" IS '是否启用重排 1启用 2禁用';
COMMENT ON COLUMN "public"."knowledge_agent"."rerank_top_k" IS '重排 topK';
COMMENT ON COLUMN "public"."knowledge_agent"."rerank_threshold" IS '重排阈值';
COMMENT ON COLUMN "public"."knowledge_agent"."fallback_strategy" IS '兜底策略 model/fixed';
COMMENT ON COLUMN "public"."knowledge_agent"."fallback_response" IS '兜底固定话术（strategy=fixed 时生效）';
COMMENT ON COLUMN "public"."knowledge_agent"."welcome" IS '开场白';
COMMENT ON COLUMN "public"."knowledge_agent"."suggested_questions" IS '推荐问题 JSON 数组';
COMMENT ON COLUMN "public"."knowledge_agent"."status" IS '1正常 2禁用';
COMMENT ON COLUMN "public"."knowledge_agent"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."knowledge_agent"."updated_at" IS '更新时间';
COMMENT ON COLUMN "public"."knowledge_agent"."rerank_model_id" IS '重排模型 ai_model.id（type=3），空用全局默认';
COMMENT ON COLUMN "public"."knowledge_agent"."rerank_model_name" IS '冗余：重排模型显示名';
COMMENT ON COLUMN "public"."knowledge_agent"."kb_mode" IS '知识库模式 all全部/selected指定/none不使用';
COMMENT ON COLUMN "public"."knowledge_agent"."document_ids" IS '限定文档 id，逗号分隔（kb_mode=selected 时可选，空=整库）';
COMMENT ON COLUMN "public"."knowledge_agent"."rewrite_model_id" IS '意图/改写专用模型 ai_model.id（type=1，对话模型），空用全局默认大模型';
COMMENT ON COLUMN "public"."knowledge_agent"."rewrite_model_name" IS '冗余：意图/改写专用模型显示名';
COMMENT ON TABLE "public"."knowledge_agent" IS '知识库智能体表';

-- ----------------------------
-- Records of knowledge_agent
-- ----------------------------

-- ----------------------------
-- Table structure for knowledge_base
-- ----------------------------
DROP TABLE IF EXISTS "public"."knowledge_base";
CREATE TABLE "public"."knowledge_base" (
  "id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "description" text COLLATE "pg_catalog"."default",
  "embedding_model_id" int4,
  "embedding_model_name" varchar(128) COLLATE "pg_catalog"."default",
  "embedding_model" varchar(64) COLLATE "pg_catalog"."default",
  "embedding_model_url" varchar(512) COLLATE "pg_catalog"."default",
  "embedding_model_api_key" varchar(512) COLLATE "pg_catalog"."default",
  "dimension" int4,
  "status" int2 NOT NULL DEFAULT 1,
  "doc_count" int4 NOT NULL DEFAULT 0,
  "kg_enabled" int2 NOT NULL DEFAULT 2,
  "created_at" timestamp(6) DEFAULT now(),
  "updated_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."knowledge_base"."id" IS '主键';
COMMENT ON COLUMN "public"."knowledge_base"."embedding_model_id" IS '绑定的嵌入模型 ai_model.id';
COMMENT ON COLUMN "public"."knowledge_base"."embedding_model_name" IS '绑定的具体模型名';
COMMENT ON COLUMN "public"."knowledge_base"."embedding_model" IS '冗余：嵌入模型显示名';
COMMENT ON COLUMN "public"."knowledge_base"."embedding_model_url" IS '创建时快照的服务地址';
COMMENT ON COLUMN "public"."knowledge_base"."embedding_model_api_key" IS '创建时快照的凭证';
COMMENT ON COLUMN "public"."knowledge_base"."dimension" IS '向量维度';
COMMENT ON COLUMN "public"."knowledge_base"."status" IS '1正常 2禁用';
COMMENT ON COLUMN "public"."knowledge_base"."doc_count" IS '文档数';
COMMENT ON COLUMN "public"."knowledge_base"."kg_enabled" IS '知识图谱开关 1=启用 2=禁用';
COMMENT ON TABLE "public"."knowledge_base" IS '知识库主表';

-- ----------------------------
-- Records of knowledge_base
-- ----------------------------

-- ----------------------------
-- Table structure for knowledge_question
-- ----------------------------
DROP TABLE IF EXISTS "public"."knowledge_question";
CREATE TABLE "public"."knowledge_question" (
  "id" int8 NOT NULL DEFAULT nextval('knowledge_question_id_seq'::regclass),
  "kb_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "document_id" varchar(64) COLLATE "pg_catalog"."default",
  "chunk_id" varchar(64) COLLATE "pg_catalog"."default",
  "content" text COLLATE "pg_catalog"."default" NOT NULL,
  "source" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'manual'::character varying,
  "status" int2 NOT NULL DEFAULT 1,
  "created_at" timestamp(6) DEFAULT now(),
  "updated_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."knowledge_question"."chunk_id" IS '关联问题 chunk chunks.id';
COMMENT ON TABLE "public"."knowledge_question" IS '知识库问题表';

-- ----------------------------
-- Records of knowledge_question
-- ----------------------------

-- ----------------------------
-- Table structure for mcp_server
-- ----------------------------
DROP TABLE IF EXISTS "public"."mcp_server";
CREATE TABLE "public"."mcp_server" (
  "id" int4 NOT NULL DEFAULT nextval('mcp_server_id_seq'::regclass),
  "name" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "description" varchar(500) COLLATE "pg_catalog"."default",
  "enabled" bool NOT NULL DEFAULT true,
  "transport_type" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
  "url" varchar(512) COLLATE "pg_catalog"."default" NOT NULL,
  "auth_type" varchar(20) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'none'::character varying,
  "auth_config" text COLLATE "pg_catalog"."default",
  "headers" text COLLATE "pg_catalog"."default",
  "timeout_sec" int4 NOT NULL DEFAULT 30,
  "retry_count" int4 NOT NULL DEFAULT 1,
  "remark" varchar(255) COLLATE "pg_catalog"."default",
  "sort" int4 NOT NULL DEFAULT 100,
  "create_time" timestamp(6) DEFAULT now(),
  "update_time" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."mcp_server"."transport_type" IS '传输类型：sse / http_streamable（底层统一用 StreamableHttpMcpTransport，sse 仅作向后兼容标签）';
COMMENT ON COLUMN "public"."mcp_server"."auth_type" IS '认证类型：none 无 / api_key 注入自定义头 / bearer 注入 Authorization: Bearer';
COMMENT ON COLUMN "public"."mcp_server"."auth_config" IS '认证 JSON：{"apiKey":"...","apiKeyHeader":"X-API-Key"} 或 {"token":"..."}';
COMMENT ON COLUMN "public"."mcp_server"."headers" IS '自定义请求头 JSON {"k":"v"}，与认证头叠加（自定义头优先级更高）';
COMMENT ON TABLE "public"."mcp_server" IS 'MCP 服务配置表';

-- ----------------------------
-- Records of mcp_server
-- ----------------------------

-- ----------------------------
-- Table structure for mcp_tool
-- ----------------------------
DROP TABLE IF EXISTS "public"."mcp_tool";
CREATE TABLE "public"."mcp_tool" (
  "id" int4 NOT NULL DEFAULT nextval('mcp_tool_id_seq'::regclass),
  "server_id" int4 NOT NULL,
  "full_id" varchar(160) COLLATE "pg_catalog"."default" NOT NULL,
  "tool_name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "description" varchar(500) COLLATE "pg_catalog"."default",
  "input_schema" text COLLATE "pg_catalog"."default",
  "last_synced_at" timestamp(6),
  "create_time" timestamp(6) DEFAULT now(),
  "update_time" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."mcp_tool"."full_id" IS '全局唯一工具标识，格式 svc{serverId}__{toolName}，与意图节点 t_intent_node.mcp_tool_id 对齐';
COMMENT ON COLUMN "public"."mcp_tool"."tool_name" IS 'MCP Server 暴露的原始工具名（callTool 时用这个名字）';
COMMENT ON TABLE "public"."mcp_tool" IS 'MCP 工具快照表';

-- ----------------------------
-- Records of mcp_tool
-- ----------------------------

-- ----------------------------
-- Table structure for parent_chunks
-- ----------------------------
DROP TABLE IF EXISTS "public"."parent_chunks";
CREATE TABLE "public"."parent_chunks" (
  "id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "kb_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "content" text COLLATE "pg_catalog"."default",
  "metadata" jsonb,
  "created_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."parent_chunks"."metadata" IS 'JSON：document_id/file_name 等';
COMMENT ON TABLE "public"."parent_chunks" IS '父块表';

-- ----------------------------
-- Records of parent_chunks
-- ----------------------------

-- ----------------------------
-- Table structure for sample_query
-- ----------------------------
DROP TABLE IF EXISTS "public"."sample_query";
CREATE TABLE "public"."sample_query" (
  "id" int8 NOT NULL DEFAULT nextval('sample_query_id_seq'::regclass),
  "question" text COLLATE "pg_catalog"."default" NOT NULL,
  "answer" text COLLATE "pg_catalog"."default" NOT NULL,
  "embedding" vector,
  "tsv" tsvector,
  "vectorized" int2 NOT NULL DEFAULT 0,
  "source" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'manual'::character varying,
  "status" int2 NOT NULL DEFAULT 1,
  "created_at" timestamp(6) DEFAULT now(),
  "updated_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."sample_query"."question" IS '问题文本，参与向量化，作为检索输入';
COMMENT ON COLUMN "public"."sample_query"."answer" IS '答案文本，命中后直接返回（不向量化）';
COMMENT ON COLUMN "public"."sample_query"."embedding" IS '问题向量（pgvector），独立存储，不写入 chunks 表';
COMMENT ON COLUMN "public"."sample_query"."vectorized" IS '0未向量化 1已向量化（embedding 是否已填充）';
COMMENT ON TABLE "public"."sample_query" IS '样例查询问答对表';

-- ----------------------------
-- Records of sample_query
-- ----------------------------

-- ----------------------------
-- Table structure for sample_query_config
-- ----------------------------
DROP TABLE IF EXISTS "public"."sample_query_config";
CREATE TABLE "public"."sample_query_config" (
  "id" int4 NOT NULL DEFAULT nextval('sample_query_config_id_seq'::regclass),
  "embedding_model_id" int4,
  "embedding_model_name" varchar(128) COLLATE "pg_catalog"."default",
  "similarity_threshold" numeric(4,3) NOT NULL DEFAULT 0.850,
  "updated_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."sample_query_config"."embedding_model_id" IS '关联 ai_model.id（type=2 向量模型）';
COMMENT ON COLUMN "public"."sample_query_config"."embedding_model_name" IS '冗余快照：具体模型名（ai_model.models 中的某一项）';
COMMENT ON COLUMN "public"."sample_query_config"."similarity_threshold" IS '命中相似度阈值（0~1），高于此值直接返回答案';
COMMENT ON TABLE "public"."sample_query_config" IS '样例查询全局配置表';

-- ----------------------------
-- Records of sample_query_config
-- ----------------------------
INSERT INTO "public"."sample_query_config" VALUES (1, 2, 'qwen3-embedding-8b', 0.850, '2026-07-22 03:56:55.991679');

-- ----------------------------
-- Table structure for t_conversation_message
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_conversation_message";
CREATE TABLE "public"."t_conversation_message" (
  "id" int8 NOT NULL DEFAULT nextval('t_conversation_message_id_seq'::regclass),
  "conversation_id" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "user_id" varchar(64) COLLATE "pg_catalog"."default",
  "role" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "content" text COLLATE "pg_catalog"."default",
  "thinking_content" text COLLATE "pg_catalog"."default",
  "created_at" timestamp(6) DEFAULT now(),
  "rag_context" jsonb
)
;
COMMENT ON COLUMN "public"."t_conversation_message"."role" IS '角色：user / assistant / system';
COMMENT ON COLUMN "public"."t_conversation_message"."thinking_content" IS '思考过程内容';
COMMENT ON COLUMN "public"."t_conversation_message"."rag_context" IS 'RAG 各阶段上下文 JSON（仅 assistant 消息，含召回/重写/重排/意图等）';
COMMENT ON TABLE "public"."t_conversation_message" IS 'RAG 会话消息表';

-- ----------------------------
-- Records of t_conversation_message
-- ----------------------------

-- ----------------------------
-- Table structure for t_conversation_summary
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_conversation_summary";
CREATE TABLE "public"."t_conversation_summary" (
  "conversation_id" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
  "user_id" varchar(64) COLLATE "pg_catalog"."default",
  "summary" text COLLATE "pg_catalog"."default",
  "last_message_id" int8,
  "updated_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."t_conversation_summary"."last_message_id" IS '增量压缩下界：已摘要的最后一条消息 id';
COMMENT ON TABLE "public"."t_conversation_summary" IS 'RAG 会话话题摘要表';

-- ----------------------------
-- Records of t_conversation_summary
-- ----------------------------

-- ----------------------------
-- Table structure for t_ingestion_pipeline_node
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_ingestion_pipeline_node";
CREATE TABLE "public"."t_ingestion_pipeline_node" (
  "id" int8 NOT NULL DEFAULT nextval('t_ingestion_pipeline_node_id_seq'::regclass),
  "pipeline_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "node_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "node_type" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "next_node_id" varchar(64) COLLATE "pg_catalog"."default",
  "settings_json" text COLLATE "pg_catalog"."default",
  "condition_json" text COLLATE "pg_catalog"."default",
  "enabled" bool NOT NULL DEFAULT true,
  "created_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."t_ingestion_pipeline_node"."node_type" IS 'fetcher/parser/enhancer/chunker/enricher/indexer';
COMMENT ON TABLE "public"."t_ingestion_pipeline_node" IS '入库流水线定义节点表';

-- ----------------------------
-- Records of t_ingestion_pipeline_node
-- ----------------------------

-- ----------------------------
-- Table structure for t_ingestion_task_node
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_ingestion_task_node";
CREATE TABLE "public"."t_ingestion_task_node" (
  "id" int8 NOT NULL DEFAULT nextval('t_ingestion_task_node_id_seq'::regclass),
  "task_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "node_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "status" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "message" text COLLATE "pg_catalog"."default",
  "duration_ms" int8,
  "created_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."t_ingestion_task_node"."status" IS 'success/failed/skipped/error';
COMMENT ON TABLE "public"."t_ingestion_task_node" IS '入库任务节点日志表';

-- ----------------------------
-- Records of t_ingestion_task_node
-- ----------------------------

-- ----------------------------
-- Table structure for t_intent_node
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_intent_node";
CREATE TABLE "public"."t_intent_node" (
  "id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "parent_id" varchar(64) COLLATE "pg_catalog"."default",
  "level" int2 NOT NULL DEFAULT 0,
  "kind" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'KB'::character varying,
  "name" varchar(128) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "description" text COLLATE "pg_catalog"."default",
  "examples" text COLLATE "pg_catalog"."default",
  "collection_name" varchar(128) COLLATE "pg_catalog"."default",
  "mcp_tool_id" varchar(64) COLLATE "pg_catalog"."default",
  "prompt_template" text COLLATE "pg_catalog"."default",
  "param_prompt_template" text COLLATE "pg_catalog"."default",
  "top_k" int4,
  "enabled" bool NOT NULL DEFAULT true,
  "deleted" bool NOT NULL DEFAULT false,
  "created_at" timestamp(6) DEFAULT now(),
  "doc_ids" text COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."t_intent_node"."level" IS '0=DOMAIN 1=CATEGORY 2=TOPIC';
COMMENT ON COLUMN "public"."t_intent_node"."kind" IS 'KB / SYSTEM / MCP';
COMMENT ON TABLE "public"."t_intent_node" IS '意图树节点表';

-- ----------------------------
-- Records of t_intent_node
-- ----------------------------

-- ----------------------------
-- Table structure for workflow
-- ----------------------------
DROP TABLE IF EXISTS "public"."workflow";
CREATE TABLE "public"."workflow" (
  "id" varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
  "name" varchar(100) COLLATE "pg_catalog"."default",
  "description" varchar(500) COLLATE "pg_catalog"."default",
  "flow_data" text COLLATE "pg_catalog"."default",
  "status" int2 DEFAULT 1,
  "created_at" timestamp(6),
  "updated_at" timestamp(6)
)
;
COMMENT ON COLUMN "public"."workflow"."id" IS '主键 UUID hex（业务生成）';
COMMENT ON COLUMN "public"."workflow"."name" IS '编排名称';
COMMENT ON COLUMN "public"."workflow"."description" IS '描述';
COMMENT ON COLUMN "public"."workflow"."flow_data" IS '流程设计 JSON（X6 graph.toJSON()）';
COMMENT ON COLUMN "public"."workflow"."status" IS '1正常 2禁用';
COMMENT ON COLUMN "public"."workflow"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."workflow"."updated_at" IS '更新时间';
COMMENT ON TABLE "public"."workflow" IS '编排流程表';

-- ----------------------------
-- Records of workflow
-- ----------------------------

-- ----------------------------
-- Table structure for workflow_runtime
-- ----------------------------
DROP TABLE IF EXISTS "public"."workflow_runtime";
CREATE TABLE "public"."workflow_runtime" (
  "id" int8 NOT NULL DEFAULT nextval('workflow_runtime_id_seq'::regclass),
  "workflow_id" varchar(32) COLLATE "pg_catalog"."default",
  "user_id" varchar(64) COLLATE "pg_catalog"."default",
  "title" varchar(255) COLLATE "pg_catalog"."default",
  "created_at" timestamp(6),
  "updated_at" timestamp(6)
)
;
COMMENT ON COLUMN "public"."workflow_runtime"."id" IS '主键（自增）';
COMMENT ON COLUMN "public"."workflow_runtime"."workflow_id" IS '编排流程 id';
COMMENT ON COLUMN "public"."workflow_runtime"."user_id" IS '用户 id';
COMMENT ON COLUMN "public"."workflow_runtime"."title" IS '首问截断（取首条问题前 255 字）';
COMMENT ON COLUMN "public"."workflow_runtime"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."workflow_runtime"."updated_at" IS '更新时间';
COMMENT ON TABLE "public"."workflow_runtime" IS '编排流程运行时表';

-- ----------------------------
-- Records of workflow_runtime
-- ----------------------------

-- ----------------------------
-- Table structure for workflow_runtime_context
-- ----------------------------
DROP TABLE IF EXISTS "public"."workflow_runtime_context";
CREATE TABLE "public"."workflow_runtime_context" (
  "id" int8 NOT NULL DEFAULT nextval('workflow_runtime_context_id_seq'::regclass),
  "runtime_id" int8,
  "node_type" varchar(55) COLLATE "pg_catalog"."default",
  "step" int2,
  "output_data" text COLLATE "pg_catalog"."default",
  "model_data" text COLLATE "pg_catalog"."default",
  "cell" varchar(155) COLLATE "pg_catalog"."default",
  "created_at" timestamp(6),
  "updated_at" timestamp(6)
)
;
COMMENT ON COLUMN "public"."workflow_runtime_context"."id" IS '主键（自增）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."runtime_id" IS '运行时 id（workflow_runtime.id）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."node_type" IS '节点类型（start-node/llm-node/answer-node/...）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."step" IS '步骤号（按执行顺序自增）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."output_data" IS '出参数据 JSON（sys.* 全局 + node.<cell> 分区产出）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."model_data" IS '模型数据 JSON（节点配置 + token 用量）';
COMMENT ON COLUMN "public"."workflow_runtime_context"."cell" IS 'X6 节点 id';
COMMENT ON COLUMN "public"."workflow_runtime_context"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."workflow_runtime_context"."updated_at" IS '更新时间';
COMMENT ON TABLE "public"."workflow_runtime_context" IS '编排流程运行时上下文表';

-- ----------------------------
-- Records of workflow_runtime_context
-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."admin_user_id_seq"
OWNED BY "public"."admin_user"."id";
SELECT setval('"public"."admin_user_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM admin_user), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."ai_model_id_seq"
OWNED BY "public"."ai_model"."id";
SELECT setval('"public"."ai_model_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM ai_model), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."ext_service_config_id_seq"
OWNED BY "public"."ext_service_config"."id";
SELECT setval('"public"."ext_service_config_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM ext_service_config), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."kg_config_id_seq"
OWNED BY "public"."kg_config"."id";
SELECT setval('"public"."kg_config_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM kg_config), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."kg_entity_id_seq"
OWNED BY "public"."kg_entity"."id";
SELECT setval('"public"."kg_entity_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM kg_entity), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."kg_extraction_record_id_seq"
OWNED BY "public"."kg_extraction_record"."id";
SELECT setval('"public"."kg_extraction_record_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM kg_extraction_record), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."knowledge_question_id_seq"
OWNED BY "public"."knowledge_question"."id";
SELECT setval('"public"."knowledge_question_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM knowledge_question), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."mcp_server_id_seq"
OWNED BY "public"."mcp_server"."id";
SELECT setval('"public"."mcp_server_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM mcp_server), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."mcp_tool_id_seq"
OWNED BY "public"."mcp_tool"."id";
SELECT setval('"public"."mcp_tool_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM mcp_tool), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."sample_query_config_id_seq"
OWNED BY "public"."sample_query_config"."id";
SELECT setval('"public"."sample_query_config_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM sample_query_config), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."sample_query_id_seq"
OWNED BY "public"."sample_query"."id";
SELECT setval('"public"."sample_query_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM sample_query), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."t_conversation_message_id_seq"
OWNED BY "public"."t_conversation_message"."id";
SELECT setval('"public"."t_conversation_message_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM t_conversation_message), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."t_ingestion_pipeline_node_id_seq"
OWNED BY "public"."t_ingestion_pipeline_node"."id";
SELECT setval('"public"."t_ingestion_pipeline_node_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM t_ingestion_pipeline_node), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."t_ingestion_task_node_id_seq"
OWNED BY "public"."t_ingestion_task_node"."id";
SELECT setval('"public"."t_ingestion_task_node_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM t_ingestion_task_node), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."workflow_runtime_context_id_seq"
OWNED BY "public"."workflow_runtime_context"."id";
SELECT setval('"public"."workflow_runtime_context_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM workflow_runtime_context), true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."workflow_runtime_id_seq"
OWNED BY "public"."workflow_runtime"."id";
SELECT setval('"public"."workflow_runtime_id_seq"', (SELECT COALESCE(MAX(id), 1) FROM workflow_runtime), true);

-- ----------------------------
-- Indexes structure for table admin_user
-- ----------------------------
CREATE UNIQUE INDEX "uk_admin_user_account" ON "public"."admin_user" USING btree (
  "account" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table admin_user
-- ----------------------------
ALTER TABLE "public"."admin_user" ADD CONSTRAINT "admin_user_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table ai_model
-- ----------------------------
CREATE INDEX "idx_ai_model_type_status" ON "public"."ai_model" USING btree (
  "type" "pg_catalog"."int2_ops" ASC NULLS LAST,
  "status" "pg_catalog"."int2_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table ai_model
-- ----------------------------
ALTER TABLE "public"."ai_model" ADD CONSTRAINT "ai_model_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table chunks
-- ----------------------------
CREATE INDEX "idx_chunks_doc" ON "public"."chunks" USING btree (
  (metadata ->> 'document_id'::text) COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_chunks_kb" ON "public"."chunks" USING btree (
  "kb_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_chunks_tsv" ON "public"."chunks" USING gin (
  "tsv" "pg_catalog"."tsvector_ops"
);

-- ----------------------------
-- Primary Key structure for table chunks
-- ----------------------------
ALTER TABLE "public"."chunks" ADD CONSTRAINT "chunks_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table document
-- ----------------------------
CREATE INDEX "idx_document_kb" ON "public"."document" USING btree (
  "kb_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table document
-- ----------------------------
ALTER TABLE "public"."document" ADD CONSTRAINT "document_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table ext_service_config
-- ----------------------------
CREATE INDEX "idx_ext_service_category" ON "public"."ext_service_config" USING btree (
  "category" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table ext_service_config
-- ----------------------------
ALTER TABLE "public"."ext_service_config" ADD CONSTRAINT "ext_service_config_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table kg_config
-- ----------------------------
ALTER TABLE "public"."kg_config" ADD CONSTRAINT "kg_config_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table kg_entity
-- ----------------------------
CREATE INDEX "idx_kg_entity_canonical" ON "public"."kg_entity" USING btree (
  "kb_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "canonical_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_kg_entity_doc" ON "public"."kg_entity" USING btree (
  "kb_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "doc_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_kg_entity_kb" ON "public"."kg_entity" USING btree (
  "kb_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_kg_entity_tsv" ON "public"."kg_entity" USING gin (
  "tsv" "pg_catalog"."tsvector_ops"
);

-- ----------------------------
-- Primary Key structure for table kg_entity
-- ----------------------------
ALTER TABLE "public"."kg_entity" ADD CONSTRAINT "kg_entity_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table kg_extraction_record
-- ----------------------------
CREATE INDEX "idx_kg_record_kb" ON "public"."kg_extraction_record" USING btree (
  "kb_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_kg_record_status" ON "public"."kg_extraction_record" USING btree (
  "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table kg_extraction_record
-- ----------------------------
ALTER TABLE "public"."kg_extraction_record" ADD CONSTRAINT "kg_extraction_record_kb_id_document_id_key" UNIQUE ("kb_id", "document_id");

-- ----------------------------
-- Primary Key structure for table kg_extraction_record
-- ----------------------------
ALTER TABLE "public"."kg_extraction_record" ADD CONSTRAINT "kg_extraction_record_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table knowledge_agent
-- ----------------------------
ALTER TABLE "public"."knowledge_agent" ADD CONSTRAINT "knowledge_agent_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table knowledge_base
-- ----------------------------
ALTER TABLE "public"."knowledge_base" ADD CONSTRAINT "knowledge_base_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table knowledge_question
-- ----------------------------
CREATE INDEX "idx_knowledge_question_kb" ON "public"."knowledge_question" USING btree (
  "kb_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table knowledge_question
-- ----------------------------
ALTER TABLE "public"."knowledge_question" ADD CONSTRAINT "knowledge_question_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table mcp_server
-- ----------------------------
CREATE INDEX "idx_mcp_server_enabled" ON "public"."mcp_server" USING btree (
  "enabled" "pg_catalog"."bool_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table mcp_server
-- ----------------------------
ALTER TABLE "public"."mcp_server" ADD CONSTRAINT "mcp_server_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table mcp_tool
-- ----------------------------
CREATE INDEX "idx_mcp_tool_server" ON "public"."mcp_tool" USING btree (
  "server_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table mcp_tool
-- ----------------------------
ALTER TABLE "public"."mcp_tool" ADD CONSTRAINT "mcp_tool_full_id_key" UNIQUE ("full_id");

-- ----------------------------
-- Primary Key structure for table mcp_tool
-- ----------------------------
ALTER TABLE "public"."mcp_tool" ADD CONSTRAINT "mcp_tool_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table parent_chunks
-- ----------------------------
CREATE INDEX "idx_parent_chunks_kb" ON "public"."parent_chunks" USING btree (
  "kb_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table parent_chunks
-- ----------------------------
ALTER TABLE "public"."parent_chunks" ADD CONSTRAINT "parent_chunks_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table sample_query
-- ----------------------------
CREATE INDEX "idx_sample_query_tsv" ON "public"."sample_query" USING gin (
  "tsv" "pg_catalog"."tsvector_ops"
);

-- ----------------------------
-- Primary Key structure for table sample_query
-- ----------------------------
ALTER TABLE "public"."sample_query" ADD CONSTRAINT "sample_query_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table sample_query_config
-- ----------------------------
ALTER TABLE "public"."sample_query_config" ADD CONSTRAINT "sample_query_config_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table t_conversation_message
-- ----------------------------
CREATE INDEX "idx_conv_msg_cid" ON "public"."t_conversation_message" USING btree (
  "conversation_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_conv_msg_rag_ctx" ON "public"."t_conversation_message" USING gin (
  "rag_context" "pg_catalog"."jsonb_ops"
);

-- ----------------------------
-- Primary Key structure for table t_conversation_message
-- ----------------------------
ALTER TABLE "public"."t_conversation_message" ADD CONSTRAINT "t_conversation_message_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table t_conversation_summary
-- ----------------------------
ALTER TABLE "public"."t_conversation_summary" ADD CONSTRAINT "t_conversation_summary_pkey" PRIMARY KEY ("conversation_id");

-- ----------------------------
-- Indexes structure for table t_ingestion_pipeline_node
-- ----------------------------
CREATE INDEX "idx_pipeline_node_pid" ON "public"."t_ingestion_pipeline_node" USING btree (
  "pipeline_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table t_ingestion_pipeline_node
-- ----------------------------
ALTER TABLE "public"."t_ingestion_pipeline_node" ADD CONSTRAINT "t_ingestion_pipeline_node_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table t_ingestion_task_node
-- ----------------------------
CREATE INDEX "idx_task_node_tid" ON "public"."t_ingestion_task_node" USING btree (
  "task_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table t_ingestion_task_node
-- ----------------------------
ALTER TABLE "public"."t_ingestion_task_node" ADD CONSTRAINT "t_ingestion_task_node_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table t_intent_node
-- ----------------------------
CREATE INDEX "idx_intent_node_parent" ON "public"."t_intent_node" USING btree (
  "parent_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table t_intent_node
-- ----------------------------
ALTER TABLE "public"."t_intent_node" ADD CONSTRAINT "t_intent_node_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table workflow
-- ----------------------------
ALTER TABLE "public"."workflow" ADD CONSTRAINT "workflow_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table workflow_runtime
-- ----------------------------
CREATE INDEX "idx_workflow_runtime_workflow_id" ON "public"."workflow_runtime" USING btree (
  "workflow_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table workflow_runtime
-- ----------------------------
ALTER TABLE "public"."workflow_runtime" ADD CONSTRAINT "workflow_runtime_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table workflow_runtime_context
-- ----------------------------
CREATE INDEX "idx_workflow_runtime_context_runtime_id" ON "public"."workflow_runtime_context" USING btree (
  "runtime_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table workflow_runtime_context
-- ----------------------------
ALTER TABLE "public"."workflow_runtime_context" ADD CONSTRAINT "uniq_workflow_runtime_context_runtime_cell" UNIQUE ("runtime_id", "cell");

-- ----------------------------
-- Primary Key structure for table workflow_runtime_context
-- ----------------------------
ALTER TABLE "public"."workflow_runtime_context" ADD CONSTRAINT "workflow_runtime_context_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Foreign Keys structure for table mcp_tool
-- ----------------------------
ALTER TABLE "public"."mcp_tool" ADD CONSTRAINT "mcp_tool_server_id_fkey" FOREIGN KEY ("server_id") REFERENCES "public"."mcp_server" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Sequences structure for chat tables
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."t_chat_message_id_seq";
CREATE SEQUENCE "public"."t_chat_message_id_seq"
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Table structure for t_chat_session
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_chat_session";
CREATE TABLE "public"."t_chat_session" (
  "id" varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
  "admin_id" int8 NOT NULL,
  "title" varchar(255) COLLATE "pg_catalog"."default",
  "description" varchar(500) COLLATE "pg_catalog"."default",
  "source" varchar(32) COLLATE "pg_catalog"."default" DEFAULT 'chat',
  "kind" varchar(16) COLLATE "pg_catalog"."default" DEFAULT 'agent',
  "agent_id" varchar(64) COLLATE "pg_catalog"."default",
  "agent_config" jsonb,
  "created_at" timestamp(6) DEFAULT now(),
  "updated_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."t_chat_session"."id" IS '会话 id（UUID hex）';
COMMENT ON COLUMN "public"."t_chat_session"."admin_id" IS '所属管理员 id（登录用户）';
COMMENT ON COLUMN "public"."t_chat_session"."title" IS '会话标题';
COMMENT ON COLUMN "public"."t_chat_session"."description" IS '会话描述';
COMMENT ON COLUMN "public"."t_chat_session"."source" IS '来源场景：chat 等';
COMMENT ON COLUMN "public"."t_chat_session"."kind" IS '目标类型：agent 智能体 / workflow 编排智能体';
COMMENT ON COLUMN "public"."t_chat_session"."agent_id" IS '绑定的智能体/编排 id';
COMMENT ON COLUMN "public"."t_chat_session"."agent_config" IS '智能体配置 JSON';
COMMENT ON COLUMN "public"."t_chat_session"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."t_chat_session"."updated_at" IS '更新时间';
COMMENT ON TABLE "public"."t_chat_session" IS '聊天会话表';
ALTER TABLE "public"."t_chat_session" ADD CONSTRAINT "t_chat_session_pkey" PRIMARY KEY ("id");
CREATE INDEX "idx_chat_session_admin_updated" ON "public"."t_chat_session" USING btree (
  "admin_id" ASC NULLS LAST,
  "updated_at" DESC NULLS LAST
);

-- ----------------------------
-- Table structure for t_chat_message
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_chat_message";
CREATE TABLE "public"."t_chat_message" (
  "id" int8 NOT NULL DEFAULT nextval('t_chat_message_id_seq'::regclass),
  "session_id" varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
  "role" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "content" text COLLATE "pg_catalog"."default",
  "references" jsonb,
  "stage_data" jsonb,
  "workflow_steps" jsonb,
  "total_cost" int8,
  "total_tokens" int4,
  "created_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."t_chat_message"."session_id" IS '所属会话 id';
COMMENT ON COLUMN "public"."t_chat_message"."role" IS '角色：user / assistant';
COMMENT ON COLUMN "public"."t_chat_message"."content" IS '消息内容';
COMMENT ON COLUMN "public"."t_chat_message"."references" IS '引用来源 JSON（assistant）';
COMMENT ON COLUMN "public"."t_chat_message"."stage_data" IS 'RAG 各阶段上下文 JSON（assistant）';
COMMENT ON COLUMN "public"."t_chat_message"."workflow_steps" IS '编排智能体步骤 JSON（assistant）';
COMMENT ON COLUMN "public"."t_chat_message"."total_cost" IS '总耗时（毫秒）';
COMMENT ON COLUMN "public"."t_chat_message"."total_tokens" IS '总 token 数';
COMMENT ON COLUMN "public"."t_chat_message"."created_at" IS '创建时间';
COMMENT ON TABLE "public"."t_chat_message" IS '聊天消息表';
ALTER TABLE "public"."t_chat_message" ADD CONSTRAINT "t_chat_message_pkey" PRIMARY KEY ("id");
CREATE INDEX "idx_chat_message_session" ON "public"."t_chat_message" USING btree (
  "session_id" COLLATE "pg_catalog"."default" "text_ops" ASC NULLS LAST
);
