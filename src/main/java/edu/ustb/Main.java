package edu.ustb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.ustb.service.*;
import edu.ustb.servlet.auth.RefreshServlet;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Map;
import java.util.LinkedHashMap;

public class Main {
    public static void main(String[] args) throws Exception {
        SqlSessionFactory factory = buildSessionFactory();
        ScoreRuleExecutor executor = new ScoreRuleExecutor();
        SurveyService surveyService = new SurveyService(factory);
        SubmissionService submissionService = new SubmissionService(factory, executor);
        AuthService authService = new AuthService(factory);
        ShareService shareService = new ShareService(factory);
        ScoreboardService scoreboardService = new ScoreboardService(factory, executor);
        SurveyQueryService surveyQueryService = new SurveyQueryService(factory);

        // 确保基础题型存在 (1 单选, 2 文本)
        ensureQuestionTypes(factory);

        Server server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

    // 静态资源：当映射为 /static/* 时，DefaultServlet 会在 baseResource 下再拼接 /static/ 前缀
    // 因此 baseResource 必须是【包含 static 子目录的父级目录】而不能直接指向 static 目录本身
    try {
        String overrideDir = System.getProperty("static.dir");
        File baseDir = null;
        if(overrideDir != null && !overrideDir.isBlank()) {
            File f = new File(overrideDir).getAbsoluteFile();
            if(f.isDirectory()) {
                if("static".equalsIgnoreCase(f.getName())) {
                    baseDir = f.getParentFile();
                    System.out.println("[INFO] -Dstatic.dir 指向 static 目录，自动上移到父目录: " + baseDir);
                } else {
                    baseDir = f; // 已经是包含 static 的父目录（或本身含 static 子目录）
                }
                if(baseDir == null || !baseDir.isDirectory()) {
                    System.err.println("[WARN] 未能确定静态资源父目录: " + overrideDir);
                }
            } else {
                System.err.println("[WARN] -Dstatic.dir 指定路径不存在或不是目录: " + overrideDir);
            }
        }
        if(baseDir == null) {
            // 回退：使用 classpath 根 (target/classes) 作为 baseResource，这样其下存在 static/
            Resource classesRoot = Resource.newClassPathResource(".");
            if(classesRoot == null) {
                System.err.println("[WARN] 未找到 classpath 根资源，静态文件可能 404。");
            } else {
                context.setBaseResource(classesRoot);
                System.out.println("[INFO] 使用 classpath 根作为静态基准: " + classesRoot);
            }
        } else {
            Resource r = Resource.newResource(baseDir);
            context.setBaseResource(r);
            System.out.println("[INFO] 使用文件系统静态基准目录: " + baseDir.getAbsolutePath());
        }
        // 诊断探测
        String[] probe = {"index.html","login.html","my-surveys.html","shared-surveys.html"};
        for(String p: probe){
            try(InputStream in = Main.class.getClassLoader().getResourceAsStream("static/"+p)){
                System.out.println("[DIAG] classpath static/"+p+" => " + (in!=null?"FOUND":"MISSING"));
            }
        }
    } catch(Exception e){
        System.err.println("[ERROR] 初始化静态资源失败: " + e.getMessage());
    }

        ServletHolder staticHolder = new ServletHolder("static", new DefaultServlet());
        staticHolder.setInitParameter("dirAllowed","false");
        context.addServlet(staticHolder, "/static/*");

        // 根路径重定向到首页
        context.addServlet(new ServletHolder(new RootRedirectServlet()), "/");

        context.addServlet(new ServletHolder(new RegisterServlet(authService)), "/api/auth/register");
        context.addServlet(new ServletHolder(new LoginServlet(authService)), "/api/auth/login");
        context.addServlet(new ServletHolder(new MeServlet(authService)), "/api/auth/me");
        context.addServlet(new ServletHolder(new LogoutServlet(authService)), "/api/auth/logout");
        context.addServlet(new ServletHolder(new RefreshServlet(authService)), "/api/auth/refresh");
        context.addServlet(new ServletHolder(new SurveyCreateServlet(surveyService, authService)), "/api/surveys");
        // 统一使用一个前缀映射 /api/surveys/* 来处理带 surveyId 的多种操作
        context.addServlet(new ServletHolder(new SurveyServlet(surveyService, submissionService, authService, shareService, scoreboardService, surveyQueryService)), "/api/surveys/*");
        context.addServlet(new ServletHolder(new SubmitServlet(submissionService)), "/api/submit");
        context.addServlet(new ServletHolder(new MySurveysServlet(authService, surveyQueryService)), "/api/surveys/mine");
        context.addServlet(new ServletHolder(new SharedSurveysServlet(authService, surveyQueryService)), "/api/surveys/shared");

        server.setHandler(context);
        server.start();
        System.out.println("Server started at http://localhost:8080");
        server.join();
    }

    private static void ensureQuestionTypes(SqlSessionFactory factory){
        try(org.apache.ibatis.session.SqlSession s = factory.openSession(true)){
            java.sql.Connection c = s.getConnection();
            try(java.sql.PreparedStatement ps = c.prepareStatement("SELECT 1 FROM question_type WHERE question_type_id=?")){
                if(!exists(ps,1)){ try(java.sql.PreparedStatement ins=c.prepareStatement("INSERT INTO question_type(question_type_id,question_type_description) VALUES(1,'单选')")){ ins.executeUpdate(); } }
                if(!exists(ps,2)){ try(java.sql.PreparedStatement ins=c.prepareStatement("INSERT INTO question_type(question_type_id,question_type_description) VALUES(2,'文本')")){ ins.executeUpdate(); } }
                if(!exists(ps,3)){ try(java.sql.PreparedStatement ins=c.prepareStatement("INSERT INTO question_type(question_type_id,question_type_description) VALUES(3,'多选')")){ ins.executeUpdate(); } }
            }
        } catch(Exception e){ System.err.println("[WARN] ensureQuestionTypes 失败: "+e.getMessage()); }
    }

    private static boolean exists(java.sql.PreparedStatement ps, int id) throws java.sql.SQLException {
        ps.setInt(1,id); try(java.sql.ResultSet rs = ps.executeQuery()){ return rs.next(); }
    }

    // 根路径 -> /static/index.html 重定向，避免 404
    static class RootRedirectServlet extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setStatus(302);
            resp.setHeader("Location", req.getContextPath()+"/static/index.html");
        }
    }

    private static SqlSessionFactory buildSessionFactory() throws Exception {
        Properties props = new Properties();
        try(InputStream in = Main.class.getClassLoader().getResourceAsStream("application.properties")) {
            if(in != null) props.load(in);
        }
    String schema = props.getProperty("db.schema", "public").trim();
    if(schema.isEmpty()) schema = "public";
    ensureSchemaAvailable(props, schema);
        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName(props.getProperty("db.driverClassName"));
        cfg.setJdbcUrl(props.getProperty("db.jdbcUrl"));
        cfg.setUsername(props.getProperty("db.username"));
        cfg.setPassword(props.getProperty("db.password"));
        cfg.setMaximumPoolSize(Integer.parseInt(props.getProperty("hikari.maximumPoolSize","5")));
        if(schema != null && !schema.isBlank()){
            cfg.setConnectionInitSql("SET search_path TO " + schema);
        }
        DataSource ds = new HikariDataSource(cfg);
        ensureDatabaseInitialized(ds, props, schema);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMappers("edu.ustb.mapper");
        // 手动组装环境
        org.apache.ibatis.mapping.Environment environment = new org.apache.ibatis.mapping.Environment("dev", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), ds);
        configuration.setEnvironment(environment);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void ensureDatabaseInitialized(DataSource ds, Properties props, String schema) {
        boolean schemaExists = false;
        try(Connection conn = ds.getConnection()){
            schemaExists = isSchemaInitialized(conn, schema);
        } catch (SQLException e) {
            System.err.println("[WARN] 无法检测数据库结构: " + e.getMessage());
        }
        if(schemaExists){
            System.out.println("[DB] 检测到现有数据表，跳过初始化脚本。");
            return;
        }
        System.out.println("[DB] 未检测到核心表，准备执行 quiz_db_init.sql。");
        HikariDataSource bootstrap = null;
        DataSource executor = ds;
        if(props != null){
            bootstrap = buildBootstrapDataSource(props, schema);
            if(bootstrap != null){
                executor = bootstrap;
                System.out.println("[DB] 使用拥有建表权限的 bootstrap 账户执行初始化脚本。");
            }
        }
        try(Connection conn = executor.getConnection()){
            runInitScript(conn, schema);
            System.out.println("[DB] 初始化脚本执行完成。");
        } catch (Exception e) {
            if(bootstrap == null){
                System.err.println("[DB] 初始化失败，当前数据库用户可能缺少建表权限。可在 application.properties 中配置 db.bootstrap.jdbcUrl/username/password 以指定拥有建表权限的账号，或提前手动执行 quiz_db_init.sql/schema.sql。");
            }
            throw new RuntimeException("初始化数据库失败", e);
        } finally {
            if(bootstrap != null){
                bootstrap.close();
            }
        }
    }

    private static HikariDataSource buildBootstrapDataSource(Properties props, String schema){
        return buildBootstrapDataSource(props, schema, true);
    }

    private static HikariDataSource buildBootstrapDataSource(Properties props, String schema, boolean applySearchPath){
        String url = props.getProperty("db.bootstrap.jdbcUrl");
        String username = props.getProperty("db.bootstrap.username");
        String password = props.getProperty("db.bootstrap.password");
        if(url == null || username == null || password == null){
            return null;
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName(props.getProperty("db.bootstrap.driverClassName", props.getProperty("db.driverClassName")));
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.bootstrap.poolSize","1")));
        cfg.setMinimumIdle(1);
        cfg.setPoolName("bootstrap-init-pool");
        if(applySearchPath && schema != null && !schema.isBlank()){
            cfg.setConnectionInitSql("SET search_path TO " + schema);
        }
        return new HikariDataSource(cfg);
    }

    private static void ensureSchemaAvailable(Properties props, String schema) {
        if(props == null || schema == null || schema.isBlank() || "public".equalsIgnoreCase(schema)){
            return;
        }
        String driver = props.getProperty("db.driverClassName");
        String url = props.getProperty("db.jdbcUrl");
        String username = props.getProperty("db.username");
        String password = props.getProperty("db.password");
        if(url == null || username == null){
            System.err.println("[WARN] 未配置 db.jdbcUrl/db.username，无法检查 schema。");
            return;
        }
        if(tryEnsureSchemaWith(driver, url, username, password, schema, username)){
            return;
        }
        HikariDataSource bootstrap = null;
        try{
            bootstrap = buildBootstrapDataSource(props, schema, false);
            if(bootstrap == null){
                throw new IllegalStateException("缺少 bootstrap 账户且应用账户无法创建 schema。");
            }
            try(Connection conn = bootstrap.getConnection()){
                if(schemaExists(conn, schema)){
                    return;
                }
                createSchema(conn, schema, username);
                System.out.println("[DB] 已通过 bootstrap 账户创建 schema " + schema);
            }
        } catch(Exception e){
            throw new RuntimeException("schema " + schema + " 不存在且无法自动创建，建议使用具有 CREATE SCHEMA 权限的账户或手动执行 CREATE SCHEMA", e);
        } finally {
            if(bootstrap != null){
                bootstrap.close();
            }
        }
    }

    private static boolean tryEnsureSchemaWith(String driver, String url, String username, String password, String schema, String owner){
        try(Connection conn = openRawConnection(driver, url, username, password)){
            if(schemaExists(conn, schema)){
                return true;
            }
            System.out.println("[DB] 未检测到 schema " + schema + "，尝试使用账号 " + username + " 创建。");
            createSchema(conn, schema, owner);
            System.out.println("[DB] 已创建 schema " + schema + " 并绑定给用户 " + owner);
            return true;
        } catch(Exception e){
            System.err.println("[WARN] 使用账号 " + username + " 创建 schema 失败: " + e.getMessage());
            return false;
        }
    }

    private static Connection openRawConnection(String driver, String url, String username, String password) throws SQLException, ClassNotFoundException {
        if(driver != null && !driver.isBlank()){
            Class.forName(driver);
        }
        return DriverManager.getConnection(url, username, password);
    }

    private static boolean schemaExists(Connection conn, String schema) throws SQLException {
        final String sql = "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?";
        try(PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, schema);
            try(ResultSet rs = ps.executeQuery()){
                return rs.next();
            }
        }
    }

    private static void createSchema(Connection conn, String schema, String owner) throws SQLException {
        if(schema == null || schema.isBlank()){
            return;
        }
        StringBuilder ddl = new StringBuilder("CREATE SCHEMA ").append(quoteIdentifier(schema));
        if(owner != null && !owner.isBlank()){
            ddl.append(" AUTHORIZATION ").append(quoteIdentifier(owner));
        }
        try(Statement st = conn.createStatement()){
            st.execute(ddl.toString());
        } catch (SQLException ex){
            if(isAlreadyExistsError(ex)){
                System.out.println("[DB] schema " + schema + " 已存在，跳过创建。");
                return;
            }
            throw ex;
        }
    }

    private static String quoteIdentifier(String value){
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static boolean isAlreadyExistsError(SQLException ex){
        String state = ex.getSQLState();
        if(state != null && (state.equals("42P06") || state.equals("42P07"))){
            return true;
        }
        String msg = ex.getMessage();
        return msg != null && msg.toLowerCase().contains("already exists");
    }

    private static boolean isSchemaInitialized(Connection conn, String schema) throws SQLException {
        final String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
        try(PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, (schema==null||schema.isBlank())?"public":schema);
            ps.setString(2, "app_user");
            try(ResultSet rs = ps.executeQuery()){
                return rs.next();
            }
        }
    }

    private static void runInitScript(Connection conn, String schema) throws IOException, SQLException {
        String override = System.getProperty("db.initScript");
        String[] candidates = (override != null && !override.isBlank())
                ? new String[]{override.trim()}
                : new String[]{"quiz_db_init.sql", "schema.sql"};
        for(String candidate : candidates){
            if(candidate == null || candidate.isBlank()) continue;
            String script = candidate.trim();
            try(InputStream in = Main.class.getClassLoader().getResourceAsStream(script)){
                if(in == null){
                    System.out.println("[DB] 未找到初始化脚本 " + script + "，继续尝试其他候选。");
                    continue;
                }
                ScriptRunner runner = new ScriptRunner(conn);
                runner.setAutoCommit(true);
                runner.setStopOnError(true);
                runner.setLogWriter(new PrintWriter(System.out));
                runner.setErrorLogWriter(new PrintWriter(System.err));
                if(schema != null && !schema.isBlank()){
                    try(java.sql.Statement st = conn.createStatement()){
                        st.execute("SET search_path TO " + schema);
                    }
                }
                try(InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)){
                    runner.runScript(reader);
                }
                return;
            }
        }
        throw new FileNotFoundException("未找到可用的初始化脚本 (quiz_db_init.sql / schema.sql)");
    }

    // --- Utility ---
    private static String readBody(HttpServletRequest req) throws IOException {
        try(BufferedReader reader = req.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line; while((line = reader.readLine())!=null){ sb.append(line); }
            return sb.toString();
        }
    }

    private static Integer authUser(AuthService authService, HttpServletRequest req){
        String token = req.getHeader("X-Auth-Token");
        if(token == null) return null; return authService.validateToken(token);
    }

    private static void writeJson(HttpServletResponse resp, int status, Object obj) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=utf-8");
        ObjectMapper om = new ObjectMapper();
        try {
            om.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            om.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        } catch(NoClassDefFoundError e){
            // 如果依赖还未生效，继续使用默认 (会在添加依赖后重启生效)
        }
        String json = om.writeValueAsString(obj);
        resp.getWriter().write(json);
    }
    public static void writeOk(HttpServletResponse resp, Object obj) throws IOException { writeJson(resp,200,obj); }
    public static void writeError(HttpServletResponse resp, int status, String code, String message) throws IOException { Map<String,Object> m=new LinkedHashMap<>(); m.put("error",code); m.put("message", message==null?code:message); writeJson(resp,status,m); }

    // --- Auth Servlets ---
    static class RegisterServlet extends HttpServlet { private final AuthService auth; private final ObjectMapper mapper = new ObjectMapper(); RegisterServlet(AuthService a){this.auth=a;} @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException { Map<String,String> m = mapper.readValue(readBody(req), new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>(){}); try { String token = auth.register(m.get("account"), m.get("name"), m.get("password")); Map<String,Object> out=new LinkedHashMap<>(); out.put("token",token); writeOk(resp,out); } catch(Exception e){ writeError(resp,400,e.getMessage(),null);} } }
    static class LoginServlet extends HttpServlet { private final AuthService auth; private final ObjectMapper mapper = new ObjectMapper(); LoginServlet(AuthService a){this.auth=a;} @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException { Map<String,String> m = mapper.readValue(readBody(req), new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>(){}); try { String token = auth.login(m.get("account"), m.get("password")); Map<String,Object> out=new LinkedHashMap<>(); out.put("token",token); writeOk(resp,out); } catch(Exception e){ writeError(resp,400,e.getMessage(),null);} } }
    static class MeServlet extends HttpServlet { private final AuthService auth; MeServlet(AuthService a){this.auth=a;} @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException { Integer uid = authUser(auth, req); if(uid==null){writeError(resp,401,"UNAUTHORIZED",null);return;} var user = auth.getUserById(uid); Map<String,Object> out=new LinkedHashMap<>(); out.put("id",user.getUserId()); out.put("account",user.getUserAccount()); out.put("name",user.getUserName()); writeOk(resp,out); } }
    static class LogoutServlet extends HttpServlet { private final AuthService auth; LogoutServlet(AuthService a){this.auth=a;} @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) { String token = req.getHeader("X-Auth-Token"); auth.logout(token); resp.setStatus(204); } }

    // --- Survey Create/Get ---
    static class SurveyCreateServlet extends HttpServlet {
        private final SurveyService surveyService; private final AuthService auth;
        SurveyCreateServlet(SurveyService surveyService, AuthService auth){ this.surveyService = surveyService; this.auth = auth; }
        @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            Integer userId = authUser(auth, req); if(userId == null){ writeError(resp,401,"UNAUTHORIZED",null); return; }
            String body = readBody(req);
            try {
                // 强制覆盖 creator_id 以防伪造
                java.util.Map<String,Object> map = new ObjectMapper().readValue(body, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>(){});
                // 兼容前端使用 title/description/questions 的风格，转换为后端期望字段
                if(map.get("survey_title") == null && map.get("title") != null){
                    map.put("survey_title", map.get("title"));
                }
                if(map.get("survey_description") == null && map.get("description") != null){
                    map.put("survey_description", map.get("description"));
                }
                Object qs = map.get("questions");
                if(qs instanceof java.util.List){
                    java.util.List<?> rawList = (java.util.List<?>) qs;
                    java.util.List<java.util.Map<String,Object>> converted = new java.util.ArrayList<>();
                    for(Object o: rawList){
                        if(!(o instanceof java.util.Map)) continue;
                        java.util.Map<?,?> src = (java.util.Map<?,?>) o;
                        java.util.Map<String,Object> qn = new java.util.HashMap<>();
                        // 题干: 前端发送 q.title -> question_content
                        Object qc = src.get("question_content");
                        if(qc == null) qc = src.get("title");
                        qn.put("question_content", qc);
                        // 类型: 前端 type (single/multi/text/number) -> question_type (1=单选,2=文本,3=多选)
                        Object typeVal = src.get("question_type");
                        if(typeVal == null) typeVal = src.get("type");
                        int mappedType = 1; // 默认单选
                        if(typeVal != null){
                            String tv = String.valueOf(typeVal);
                            if("single".equalsIgnoreCase(tv)) mappedType = 1;
                            else if("text".equalsIgnoreCase(tv) || "number".equalsIgnoreCase(tv)) mappedType = 2;
                            else if("multi".equalsIgnoreCase(tv)) mappedType = 3;
                            else {
                                try { mappedType = Integer.parseInt(tv); } catch(Exception ignore) {}
                            }
                        }
                        qn.put("question_type", mappedType);
                        // 评分规则: 前端 scoreRule 字符串 -> { rule_js_func, full_score }
                        Object sr = src.get("score_rule");
                        if(sr == null) sr = src.get("scoreRule");
                        if(sr instanceof java.util.Map){
                            qn.put("score_rule", sr);
                        } else if(sr instanceof String){
                            String code = (String) sr;
                            if(!code.isBlank()){
                                java.util.Map<String,Object> rule = new java.util.HashMap<>();
                                rule.put("rule_js_func", code);
                                Object full = src.get("fullScore");
                                if(full instanceof Number) rule.put("full_score", ((Number) full).doubleValue());
                                qn.put("score_rule", rule);
                            }
                        } else {
                            Object code = src.get("scoreRule");
                            if(code instanceof String && !((String)code).isBlank()){
                                java.util.Map<String,Object> rule = new java.util.HashMap<>();
                                rule.put("rule_js_func", code);
                                Object full = src.get("fullScore");
                                if(full instanceof Number) rule.put("full_score", ((Number) full).doubleValue());
                                qn.put("score_rule", rule);
                            }
                        }
                        // 选项: 前端 q.options = ["A内容","B内容"] 或对象数组 -> 标准化 [{label:'A',content:'A内容'}]
                        Object opts = src.get("options");
                        if(opts instanceof java.util.List){
                            java.util.List<?> ol = (java.util.List<?>) opts;
                            java.util.List<java.util.Map<String,Object>> norm = new java.util.ArrayList<>();
                            int ord = 0;
                            for(Object ov : ol){
                                java.util.Map<String,Object> np = new java.util.HashMap<>();
                                if(ov instanceof String){
                                    String text = (String) ov; np.put("label", String.valueOf((char)('A'+(ord%26)))); np.put("content", text);
                                } else if(ov instanceof java.util.Map){
                                    java.util.Map<?,?> mv = (java.util.Map<?,?>) ov; Object lbl = mv.get("label"); Object ct = mv.get("content"); if(lbl==null) lbl = String.valueOf((char)('A'+(ord%26))); np.put("label", lbl); np.put("content", ct); }
                                norm.add(np); ord++;
                            }
                            qn.put("options", norm);
                        }
                        converted.add(qn);
                    }
                    map.put("questions", converted);
                }
                map.put("creator_id", userId);
                String newJson = new ObjectMapper().writeValueAsString(map);
                var survey = surveyService.createSurveyFromJson(newJson);
                writeOk(resp,survey);
            } catch (Exception e) { writeError(resp,500,"INTERNAL_ERROR",e.getMessage()); }
        }
    }


    // --- Submission ---
    static class SubmitServlet extends HttpServlet { private final SubmissionService submissionService; SubmitServlet(SubmissionService s){ this.submissionService=s;} @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException { String body = readBody(req); try { double score = submissionService.submit(body, req.getRemoteAddr()); Map<String,Object> out=new LinkedHashMap<>(); out.put("score",score); writeOk(resp,out); } catch (Exception e){ writeError(resp,500,"INTERNAL_ERROR",e.getMessage()); } } }
    static class SubmitBatchServlet extends HttpServlet { private final SubmissionService submissionService; SubmitBatchServlet(SubmissionService s){this.submissionService=s;} @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException { String path = req.getPathInfo(); if(path==null || path.split("/").length<2){resp.setStatus(400);resp.getWriter().write("Bad path");return;} Integer surveyId = Integer.parseInt(path.split("/")[1]); String body = readBody(req); double total = submissionService.submitBatch(surveyId, body, req.getRemoteAddr()); resp.setContentType("application/json"); resp.getWriter().write("{\"total_score\":"+total+"}"); } }

    // 统一 Survey 操作 servlet: GET /api/surveys/{id}
    // POST /api/surveys/{id}/submitList  (批量提交)
    // POST /api/surveys/{id}/share      (创建分享)
    // GET  /api/surveys/{id}/scoreboard (记分榜)
    // GET  /api/surveys/{id}/shares     (分享成员列表)
    static class SurveyServlet extends HttpServlet {
        private final SurveyService surveyService; private final SubmissionService submissionService; private final AuthService auth; private final ShareService shareService; private final ScoreboardService scoreboardService; private final SurveyQueryService queryService; private final ObjectMapper mapper = new ObjectMapper();
        SurveyServlet(SurveyService ss, SubmissionService sub, AuthService a, ShareService sh, ScoreboardService sb, SurveyQueryService q){ this.surveyService=ss; this.submissionService=sub; this.auth=a; this.shareService=sh; this.scoreboardService=sb; this.queryService=q; }
        private Integer extractId(String path){ if(path==null) return null; String[] seg = path.split("/"); if(seg.length<2) return null; try { return Integer.parseInt(seg[1]); } catch(Exception e){ return null; } }
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String path = req.getPathInfo(); if(path==null || "/".equals(path)){ writeError(resp,400,"MISSING_ID",null); return; }
            // 判定子路径
            if(path.matches("^/\\d+$")) { // 获取问卷
                Integer id = extractId(path); var survey = surveyService.loadSurvey(id); if(survey==null){writeError(resp,404,"NOT_FOUND",null);return;} writeOk(resp,survey); return; }
            if(path.endsWith("/scoreboard")) { Integer id = extractId(path); if(id==null){writeError(resp,400,"BAD_PATH",null);return;} var list = scoreboardService.buildScoreboard(id); writeOk(resp,list); return; }
            if(path.endsWith("/shares")) { Integer uid = authUser(auth, req); if(uid==null){writeError(resp,401,"UNAUTHORIZED",null);return;} Integer id = extractId(path); if(id==null){writeError(resp,400,"BAD_PATH",null);return;} int page = parseInt(req.getParameter("page"),1); int pageSize = parseInt(req.getParameter("page_size"),50); try { var res = queryService.pageSurveyShares(id, uid, page, pageSize); writeOk(resp,res); } catch(RuntimeException ex){ if("FORBIDDEN".equals(ex.getMessage())){ writeError(resp,403,"FORBIDDEN",null);} else { writeError(resp,500,"INTERNAL_ERROR",ex.getMessage()); } } return; }
            writeError(resp,404,"UNKNOWN_PATH",path);
        }
        @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String path = req.getPathInfo(); if(path==null){writeError(resp,400,"BAD_PATH",null);return;}
            if(path.endsWith("/submitList")) { Integer id = extractId(path); if(id==null){writeError(resp,400,"BAD_PATH",null);return;} String body = readBody(req); double total = submissionService.submitBatch(id, body, req.getRemoteAddr()); Map<String,Object> out=new LinkedHashMap<>(); out.put("total_score",total); writeOk(resp,out); return; }
            if(path.endsWith("/share")) { Integer uid = authUser(auth, req); if(uid==null){ writeError(resp,401,"UNAUTHORIZED",null); return; } Integer id = extractId(path); if(id==null){writeError(resp,400,"BAD_PATH",null);return;} Map<String,Object> body = mapper.readValue(readBody(req), new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}); boolean allowEdit = Boolean.TRUE.equals(body.get("allow_edit")); String link = shareService.createShare(id, uid, allowEdit); String qr = shareService.generateQrBase64(link); Map<String,Object> res = new LinkedHashMap<>(); res.put("link",link); res.put("qr_base64_png",qr); writeOk(resp,res); return; }
            writeError(resp,404,"UNKNOWN_PATH",path);
        }
    @Override protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException { String path=req.getPathInfo(); if(path==null){writeError(resp,400,"BAD_PATH",null);return;} Integer uid=authUser(auth, req); if(uid==null){writeError(resp,401,"UNAUTHORIZED",null);return;} if(path.matches("^/\\d+$")){ Integer id=extractId(path); Map<String,Object> body=mapper.readValue(readBody(req), new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}); String title=(String) body.getOrDefault("title"," "); String description=(String) body.getOrDefault("description",""); try { boolean ok=surveyService.updateSurveyBasic(id, uid, title, description); if(!ok){ writeError(resp,404,"NOT_FOUND",null);} else { Map<String,Object> out=new LinkedHashMap<>(); out.put("status","OK"); writeOk(resp,out);} } catch(RuntimeException ex){ if("FORBIDDEN".equals(ex.getMessage())){ writeError(resp,403,"FORBIDDEN",null);} else { writeError(resp,500,"INTERNAL_ERROR",ex.getMessage()); } } return; } if(path.matches("^/\\d+/structure$")){ Integer id=extractId(path); String bodyStr=readBody(req); try { boolean ok=surveyService.replaceStructure(id, uid, bodyStr); if(!ok){ writeError(resp,404,"NOT_FOUND",null);} else { Map<String,Object> out=new LinkedHashMap<>(); out.put("status","OK"); writeOk(resp,out);} } catch(RuntimeException ex){ if("FORBIDDEN".equals(ex.getMessage())){ writeError(resp,403,"FORBIDDEN",null);} else { writeError(resp,400,"BAD_STRUCTURE",ex.getMessage()); } } catch(Exception e){ writeError(resp,400,"INVALID_JSON",e.getMessage()); } return; } writeError(resp,404,"UNKNOWN_PATH",path); }
        @Override protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException { String path=req.getPathInfo(); if(path==null || !path.matches("^/\\d+$")){writeError(resp,400,"BAD_PATH",null);return;} Integer id=extractId(path); Integer uid=authUser(auth, req); if(uid==null){writeError(resp,401,"UNAUTHORIZED",null);return;} try { boolean ok=surveyService.deleteSurvey(id, uid); if(!ok){writeError(resp,404,"NOT_FOUND",null);} else { resp.setStatus(204); } } catch(RuntimeException ex){ if("FORBIDDEN".equals(ex.getMessage())){writeError(resp,403,"FORBIDDEN",null);} else {writeError(resp,500,"INTERNAL_ERROR",ex.getMessage()); } } }
    }

    // --- Share ---

    // --- Listing ---
    static class MySurveysServlet extends HttpServlet { private final AuthService auth; private final SurveyQueryService query; MySurveysServlet(AuthService a, SurveyQueryService q){this.auth=a; this.query=q;} @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException { Integer uid = authUser(auth, req); if(uid==null){writeError(resp,401,"UNAUTHORIZED",null);return;} int page = parseInt(req.getParameter("page"),1); int pageSize = parseInt(req.getParameter("page_size"),20); var res = query.pageMySurveys(uid, page, pageSize); writeOk(resp,res); } }
    static class SharedSurveysServlet extends HttpServlet { private final AuthService auth; private final SurveyQueryService query; SharedSurveysServlet(AuthService a, SurveyQueryService q){this.auth=a; this.query=q;} @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException { Integer uid = authUser(auth, req); if(uid==null){writeError(resp,401,"UNAUTHORIZED",null);return;} int page = parseInt(req.getParameter("page"),1); int pageSize = parseInt(req.getParameter("page_size"),20); var res = query.pageSharedToMe(uid, page, pageSize); writeOk(resp,res); } }
    private static int parseInt(String v, int d){ if(v==null) return d; try { int x=Integer.parseInt(v); return x<=0?d:x; } catch(Exception e){ return d; }}
}