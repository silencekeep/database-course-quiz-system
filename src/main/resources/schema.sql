-- =============================================================
-- Schema Initialization Script (openGauss compatible, no PG 9.5+ features)
-- =============================================================
-- 用途: 统一重建应用所需全部表 (snake_case)。
-- 注意: 本脚本会 DROP 已有表 (CASCADE) 并重新创建，生产环境请谨慎。
-- 兼容性: 移除了 PostgreSQL 9.5+ 的 ON CONFLICT 以及 CREATE INDEX IF NOT EXISTS。
-- 如果当前 openGauss 版本不支持 SERIAL，可改为：
--   创建序列并 DEFAULT nextval('seq_name')；此处暂使用 SERIAL (大多数版本支持)。
-- 事务: 为避免中途错误导致整批放弃，这里不再使用单一 BEGIN;COMMIT 包裹。
--       建议确保脚本无语法错误后再执行，或手动加事务。
-- 授权角色 quiz_app 如不存在，请先创建：
--   CREATE USER quiz_app WITH PASSWORD 'your_password'; (或适配 openGauss 语法)
-- =============================================================

-- （如需整体事务，可自行取消注释）
-- BEGIN;

BEGIN;

-- 1. Drop existing tables (ignore order via CASCADE)
DROP TABLE IF EXISTS survey_share CASCADE;
DROP TABLE IF EXISTS submit_record CASCADE;
DROP TABLE IF EXISTS score_rule CASCADE;
DROP TABLE IF EXISTS text_answer CASCADE;
DROP TABLE IF EXISTS char_answer CASCADE;
DROP TABLE IF EXISTS answer CASCADE;
DROP TABLE IF EXISTS option_item CASCADE;
DROP TABLE IF EXISTS question CASCADE;
DROP TABLE IF EXISTS question_type CASCADE;
DROP TABLE IF EXISTS survey CASCADE;
DROP TABLE IF EXISTS app_user CASCADE;

-- 2. Base tables
CREATE TABLE app_user (
  user_id        SERIAL PRIMARY KEY,
  user_account   VARCHAR(255) UNIQUE NOT NULL,
  user_name      VARCHAR(255) NOT NULL,
  user_password  VARCHAR(255) NOT NULL,
  user_register_time DATE NOT NULL DEFAULT CURRENT_DATE,
  user_last_login_time DATE
);

CREATE TABLE survey (
  survey_id         SERIAL PRIMARY KEY,
  survey_title      VARCHAR(255) NOT NULL,
  survey_description TEXT,
  survey_owner_id   INT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
  survey_scoreboard SMALLINT,
  survey_show_score SMALLINT,
  survey_create_time DATE NOT NULL DEFAULT CURRENT_DATE,
  survey_expire_time DATE
);

CREATE TABLE question_type (
  question_type_id INT PRIMARY KEY,
  question_type_description VARCHAR(255)
);

-- 初始化 question_type (避免 ON CONFLICT，使用条件插入)
INSERT INTO question_type(question_type_id, question_type_description)
SELECT 1, '单选'
WHERE NOT EXISTS (SELECT 1 FROM question_type WHERE question_type_id = 1);

INSERT INTO question_type(question_type_id, question_type_description)
SELECT 2, '文本'
WHERE NOT EXISTS (SELECT 1 FROM question_type WHERE question_type_id = 2);

CREATE TABLE score_rule (
  rule_id        SERIAL PRIMARY KEY,
  rule_owner_id  INT,
  rule_js_func   VARCHAR(1024),
  rule_full_score FLOAT4
);

CREATE TABLE question (
  question_id            SERIAL PRIMARY KEY,
  question_survey_id     INT NOT NULL REFERENCES survey(survey_id) ON DELETE CASCADE,
  question_content       VARCHAR(255) NOT NULL,
  question_type          INT NOT NULL REFERENCES question_type(question_type_id),
  question_score_rule_id INT REFERENCES score_rule(rule_id)
);

CREATE TABLE option_item (
  option_id          SERIAL PRIMARY KEY,
  option_question_id INT NOT NULL REFERENCES question(question_id) ON DELETE CASCADE,
  option_order_id    INT,
  option_label       VARCHAR(8),
  option_content     VARCHAR(255)
);

CREATE TABLE answer (
  answer_id          SERIAL PRIMARY KEY,
  answer_question_id INT NOT NULL REFERENCES question(question_id) ON DELETE CASCADE,
  answer_stem_order_id INT
);

CREATE TABLE char_answer (
  answer_id     INT PRIMARY KEY REFERENCES answer(answer_id) ON DELETE CASCADE,
  answer_letter CHAR(1)
);

CREATE TABLE text_answer (
  answer_id    INT PRIMARY KEY REFERENCES answer(answer_id) ON DELETE CASCADE,
  answer_text  VARCHAR(1024)
);

CREATE TABLE submit_record (
  submit_id          SERIAL PRIMARY KEY,
  submit_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  submit_question_id INT NOT NULL REFERENCES question(question_id) ON DELETE CASCADE,
  submit_answer      VARCHAR(1024),
  submit_ip_addr     VARCHAR(255)
);

CREATE TABLE survey_share (
  share_id         SERIAL PRIMARY KEY,
  share_survey_id  INT NOT NULL REFERENCES survey(survey_id) ON DELETE CASCADE,
  share_user_id    INT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
  share_allow_edit SMALLINT DEFAULT 0
);

-- 3. Indexes (performance)
-- 重建索引（无 IF NOT EXISTS；若重复执行可先 DROP INDEX IF EXISTS）
DROP INDEX IF EXISTS idx_survey_owner;
CREATE INDEX idx_survey_owner      ON survey(survey_owner_id);
DROP INDEX IF EXISTS idx_question_survey;
CREATE INDEX idx_question_survey   ON question(question_survey_id);
DROP INDEX IF EXISTS idx_option_question;
CREATE INDEX idx_option_question   ON option_item(option_question_id);
DROP INDEX IF EXISTS idx_answer_question;
CREATE INDEX idx_answer_question   ON answer(answer_question_id);
DROP INDEX IF EXISTS idx_submit_question;
CREATE INDEX idx_submit_question   ON submit_record(submit_question_id);
DROP INDEX IF EXISTS idx_share_survey;
CREATE INDEX idx_share_survey      ON survey_share(share_survey_id);
DROP INDEX IF EXISTS idx_share_user;
CREATE INDEX idx_share_user        ON survey_share(share_user_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO quiz_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO quiz_app;

COMMIT;

-- （如前面手动启用了事务，最后再 COMMIT;）
-- COMMIT;
-- 回滚方案: 重新执行本脚本或单独 DROP 相关表。
