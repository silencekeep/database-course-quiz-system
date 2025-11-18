package edu.ustb.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.ustb.service.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end API test using embedded Jetty + H2. */
public class WebApiIntegrationTest {
    static Server server;
    static int port;
    static SqlSessionFactory factory;
    static ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void start() throws Exception {
        // DataSource (H2)
        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName("org.h2.Driver");
        cfg.setJdbcUrl("jdbc:h2:mem:quiz_api;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        cfg.setUsername("sa"); cfg.setPassword("");
        DataSource ds = new HikariDataSource(cfg);
        // init minimal schema (reuse TestMyBatisUtil via reflection avoided; inline DDL for isolation)
        try(var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute("CREATE TABLE \"Survey\"(survey_id INT PRIMARY KEY, survey_title VARCHAR(255), survey_owner_id INT, survey_scoreboard INT, survey_show_score INT, survey_create_time DATE, survey_expire_time DATE)");
            st.execute("CREATE TABLE \"Question\"(question_id INT PRIMARY KEY, question_survey_id INT, question_content VARCHAR(500), question_type INT, question_score_rule_id INT)");
            st.execute("CREATE TABLE \"Option\"(option_id INT PRIMARY KEY, option_question_id INT, option_order_id INT, option_label VARCHAR(50), option_content VARCHAR(255))");
            st.execute("CREATE TABLE \"ScoreRule\"(rule_id INT PRIMARY KEY, rule_owner_id INT, rule_js_func CLOB, rule_full_score FLOAT)");
            st.execute("CREATE TABLE \"User\"(user_id INT PRIMARY KEY, user_account VARCHAR(100), user_name VARCHAR(100), user_password VARCHAR(100), user_last_login BIGINT)");
            st.execute("CREATE TABLE \"SurveyShare\"(share_id INT PRIMARY KEY, share_survey_id INT, share_user_id INT, share_allow_edit INT)");
            st.execute("CREATE TABLE \"SubmitRecord\"(submit_id INT PRIMARY KEY, submit_time DATE, submit_question_id INT, submit_answer VARCHAR(1000), submit_ip_addr VARCHAR(64))");
            st.execute("CREATE TABLE \"Answer\"(answer_id INT PRIMARY KEY, answer_question_id INT, answer_stem_order_id INT)");
            st.execute("CREATE TABLE \"CharAnswer\"(answer_id INT PRIMARY KEY, answer_letter VARCHAR(50))");
            st.execute("CREATE TABLE \"TextAnswer\"(answer_id INT PRIMARY KEY, answer_text CLOB)");
        }
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMappers("edu.ustb.mapper");
        org.apache.ibatis.mapping.Environment environment = new org.apache.ibatis.mapping.Environment("api", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), ds);
        configuration.setEnvironment(environment);
        factory = new SqlSessionFactoryBuilder().build(configuration);
        // services
        ScoreRuleExecutor executor = new ScoreRuleExecutor();
        SurveyService surveyService = new SurveyService(factory);
        SubmissionService submissionService = new SubmissionService(factory, executor);
        AuthService authService = new AuthService(factory);
        ShareService shareService = new ShareService(factory);
        ScoreboardService scoreboardService = new ScoreboardService(factory, executor);
        SurveyQueryService surveyQueryService = new SurveyQueryService(factory);
        // jetty
        port = 19080 + (int)(System.nanoTime()%1000);
        server = new Server(port);
        ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/");
    ctx.addServlet(new ServletHolder(new TestServlets.Register(authService)), "/api/auth/register");
    ctx.addServlet(new ServletHolder(new TestServlets.Login(authService)), "/api/auth/login");
    ctx.addServlet(new ServletHolder(new TestServlets.CreateSurvey(surveyService, authService)), "/api/surveys");
    ctx.addServlet(new ServletHolder(new TestServlets.UnifiedSurvey(surveyService, submissionService, authService, shareService, scoreboardService, surveyQueryService)), "/api/surveys/*");
        server.setHandler(ctx);
        server.start();
    }

    @AfterAll
    static void stop() throws Exception { if(server!=null) server.stop(); }

    @Test
    void testFullFlow() throws Exception {
        // 1. register
        String token = postJson("/api/auth/register", Map.of("account","u1","name","U1","password","p1"))
                .get("token").toString();
        assertNotNull(token);
        // 2. create survey (one choice question + rule) via raw JSON
        String surveyJson = "{\n" +
                "  \"survey_title\": \"Flow Survey\",\n" +
                "  \"creator_id\": 999,\n" + // will be overridden
                "  \"questions\": [ {\n" +
                "    \"question_content\": \"Pick one\",\n" +
                "    \"question_type\": 1,\n" +
                "    \"question_score_rule\": { \"rule_js_func\": \"function score(a){if(a==='A') return 2; return 0;}\" },\n" +
                "    \"options\": [\n" +
                "      {\"option_label\":\"A\",\"option_content\":\"OptA\"},\n" +
                "      {\"option_label\":\"B\",\"option_content\":\"OptB\"}\n" +
                "    ]\n" +
                "  } ]\n" +
                "}";
        Map<String,Object> created = postJsonWithToken("/api/surveys", surveyJson, token);
        int surveyId = (int) created.get("surveyId");
        assertEquals("Flow Survey", created.get("surveyTitle"));
        // extract question id
        @SuppressWarnings("unchecked") List<Map<String,Object>> qs = (List<Map<String,Object>>) created.get("questions");
        int qid = (int) qs.get(0).get("questionId");
        // 3. batch submit
        String batch = "[ {\"question_id\": "+qid+", \"answer\": \"A\"}, {\"question_id\": "+qid+", \"answer\": \"B\"} ]";
    Map<String,Object> batchRes = postJsonRaw("/api/surveys/"+surveyId+"/submitList", batch);
    assertEquals(2.0d, ((Number)batchRes.get("total_score")).doubleValue(), 0.0001);
        // 4. scoreboard
        List<?> scoreboard = getJsonList("/api/surveys/"+surveyId+"/scoreboard");
        assertFalse(scoreboard.isEmpty());
        // 5. share
        Map<String,Object> shareRes = postJsonWithToken("/api/surveys/"+surveyId+"/share", "{\"allow_edit\":true}", token);
        assertTrue(shareRes.get("link").toString().contains(String.valueOf(surveyId)));
        // 6. shares list
        Map<String,Object> shareList = getJsonMapWithToken("/api/surveys/"+surveyId+"/shares", token);
        assertEquals(1, ((List<?>)shareList.get("items")).size());
    }

    // ---- HTTP helpers ----
    private static Map<String,Object> postJson(String path, Map<String,Object> body) throws Exception {
        return postJsonWithHeaders(path, mapper.writeValueAsString(body), null);
    }
    private static Map<String,Object> postJsonWithToken(String path, String bodyJson, String token) throws Exception {
        return postJsonWithHeaders(path, bodyJson, token);
    }
    private static Map<String,Object> postJsonRaw(String path, String bodyJson) throws Exception { return postJsonWithHeaders(path, bodyJson, null); }

    private static Map<String,Object> postJsonWithHeaders(String path, String bodyJson, String token) throws Exception {
        URL url = new URL("http://localhost:"+port+path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","application/json");
        if(token!=null) conn.setRequestProperty("X-Auth-Token", token);
        try(OutputStream os = conn.getOutputStream()) { os.write(bodyJson.getBytes()); }
        try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line);
            String text = sb.toString();
            if(text.startsWith("{")) return mapper.readValue(text, new TypeReference<Map<String,Object>>(){});
            return mapper.readValue(text, new TypeReference<Map<String,Object>>(){});
        }
    }

    private static List<?> getJsonList(String path) throws Exception {
        URL url = new URL("http://localhost:"+port+path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line);
            return mapper.readValue(sb.toString(), List.class);
        }
    }
    private static Map<String,Object> getJsonMapWithToken(String path, String token) throws Exception {
        URL url = new URL("http://localhost:"+port+path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Auth-Token", token);
        try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line);
            return mapper.readValue(sb.toString(), new TypeReference<Map<String,Object>>(){});
        }
    }
}
