# 数据库系统原理（双语）Web实验

---

> 项目名：问卷系统 (Jetty + MyBatis + openGauss)

## 成员

* 王思涵 计231

* 王宇航 计231

* 陈家煜 计231

## 功能概述

- 用户注册/登录 (简单内存 Token) `/api/auth/register` `/api/auth/login`
- 创建问卷(需登录) `/api/surveys` (POST)
- 获取问卷详情 `/api/surveys/{id}` (GET)
- 匿名单题提交 `/api/submit`
- 匿名批量提交(按问卷) `/api/surveys/{id}/submitList`
- 问卷分享生成链接+二维码 `/api/surveys/{id}/share` (需登录)
- 记分板 `/api/surveys/{id}/scoreboard`
- JS 评分规则：每题可配置 `score_rule.rule_js_func`

## 主要技术

- Jetty 作为嵌入式服务器
- MyBatis + HikariCP 访问 openGauss
- openGauss采用Docker部署
- Jackson 处理 JSON
- Nashorn 执行评分 JS 函数

## 数据库准备

使用提供的建表 SQL (调整 schema 名称按需)。示例连接信息在 `application.properties`：

```
db.driverClassName=org.opengauss.Driver
db.jdbcUrl=jdbc:opengauss://10.90.1.31:5432/quizdb
db.username=omm
db.password=omm@dbms123
```

确保用户及数据库已创建并具备建表与写入权限。如 openGauss 每个用户自带同名 schema，可通过 `db.schema` 指定实际建表 schema（例如 `db.schema=quiz_app`），程序会在连接阶段自动 `SET search_path`。

从现在起，应用在启动阶段会检测 `app_user` 表是否存在；若不存在，将自动执行 `quiz_db_init.sql`（若该文件缺失则回退到 `schema.sql`）完成建表与必要的样例数据初始化，因此首次运行通常无需手工导入脚本。如需替换为自定义脚本，可在启动时通过 `-Ddb.initScript=custom.sql` 指定新的类路径 SQL 文件。

如果业务账号（如 `quiz_app`）缺少建表权限，可在 `application.properties` 中额外配置 `db.bootstrap.jdbcUrl` / `db.bootstrap.username` / `db.bootstrap.password` 指向拥有权限的数据库账号（例如 openGauss 的 `omm`）。初始化脚本只会使用该账号建立连接执行一次，随后自动关闭；运行期仍会使用普通业务账号访问数据库。

> 如果需要变更数据库名，请在 `jdbc:opengauss://10.90.1.31:5432/quizdb` 中替换 `quizdb` 并保证已在 openGauss 中创建。例如：`create database quizdb;`。密码包含 `@` 不需要转义。

## 评分规则示例

提交创建问卷 JSON 时包含：

```json
{
  "survey_title": "测试问卷",
  "creator_id": 1,
  "questions": [
    {
      "question_content": "选择最佳选项",
      "options": [
        {"label":"A", "content":"优秀"},
        {"label":"B", "content":"良好"}
      ],
      "score_rule": {
        "rule_js_func": "function score(answer){ if(answer==='A') return 10; if(answer==='B') return 5; return 0; }",
        "full_score": 10
      }
    }
  ]
}
```

## 启动

```bash
mvn -q clean package
mvn -q exec:java -Dexec.mainClass=edu.ustb.Main
```

或直接运行 `Main.main`。

## 接口示例

### 注册

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"account":"user1","name":"U1","password":"pass"}'
```

返回：`{"token":"..."}`

### 登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"user1","password":"pass"}'
```

返回：`{"token":"..."}`

### 创建问卷 (需携带 Token)

```bash
curl -X POST http://localhost:8080/api/surveys \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: <TOKEN>" \
  -d '{"survey_title":"Demo","questions":[{"question_content":"Q1?","question_type":1,"options":[{"label":"A","content":"Yes"},{"label":"B","content":"No"}],"score_rule":{"rule_js_func":"function score(answer){return answer==="A"?5:0;}","full_score":5}},{"question_content":"请填写意见","question_type":2,"score_rule":{"rule_js_func":"function score(answer){return 0;}","full_score":0}}]}'
```

返回包含系统生成的 `surveyId`、`questionId` 等。

### 获取问卷

```bash
curl http://localhost:8080/api/surveys/{surveyId}
```

### 提交答案

```bash
curl -X POST http://localhost:8080/api/submit \
  -H "Content-Type: application/json" \
  -d '{"question_id":123456,"answer":"A"}'
```

返回：

```json
{"score":5}
```

### 批量提交

```bash
curl -X POST http://localhost:8080/api/surveys/{surveyId}/submitList \
  -H "Content-Type: application/json" \
  -d '[{"question_id":111,"answer":"A"},{"question_id":112,"answer":"一些文本"}]'
```

返回：`{"total_score":5}`

### 生成分享链接与二维码 (需登录)

```bash
curl -X POST http://localhost:8080/api/surveys/{surveyId}/share \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: <TOKEN>" \
  -d '{"allow_edit":false}'
```

返回示例：

```json
{
  "link":"http://localhost:8080/api/surveys/123?shareId=456",
  "qr_base64_png":"iVBORw0KGgo..."
}
```

### 记分板

```bash
curl http://localhost:8080/api/surveys/{surveyId}/scoreboard
```

返回：`[{"ip":"127.0.0.1","total":15.0,"avg":5.0}]`

## 前端

前端仅需：

1. 获取问卷 JSON -> 动态渲染题干与选项。
2. 用户选择后 POST `/api/submit`。

## 注意事项

- openGauss 需要合适驱动版本，注意Docker中下载的版本，与PostgreSQL对应。

## 
