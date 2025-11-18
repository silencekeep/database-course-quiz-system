package edu.ustb.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

public class TestMyBatisUtil {
    public static SqlSessionFactory buildFactory() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName("org.h2.Driver");
        cfg.setJdbcUrl("jdbc:h2:mem:quiz;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        cfg.setUsername("sa");
        cfg.setPassword("");
        DataSource ds = new HikariDataSource(cfg);
        initSchema(ds);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMappers("edu.ustb.mapper");
        org.apache.ibatis.mapping.Environment environment = new org.apache.ibatis.mapping.Environment("test", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), ds);
        configuration.setEnvironment(environment);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void initSchema(DataSource ds) throws Exception {
        try(Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            // Simplified schema (subset of needed columns)
            st.execute("CREATE TABLE \"Survey\"(survey_id INT PRIMARY KEY, survey_title VARCHAR(255), survey_owner_id INT, survey_scoreboard INT, survey_show_score INT, survey_create_time DATE, survey_expire_time DATE)");
            st.execute("CREATE TABLE \"Question\"(question_id INT PRIMARY KEY, question_survey_id INT, question_content VARCHAR(500), question_type INT, question_score_rule_id INT)");
            st.execute("CREATE TABLE \"Option\"(option_id INT PRIMARY KEY, option_question_id INT, option_order_id INT, option_label VARCHAR(50), option_content VARCHAR(255))");
            st.execute("CREATE TABLE \"ScoreRule\"(rule_id INT PRIMARY KEY, rule_owner_id INT, rule_js_func CLOB, rule_full_score FLOAT)");
            st.execute("CREATE TABLE \"User\"(user_id INT PRIMARY KEY, user_account VARCHAR(100), user_name VARCHAR(100), user_password VARCHAR(100), user_last_login BIGINT)");
            st.execute("CREATE TABLE \"SurveyShare\"(share_id INT PRIMARY KEY, share_survey_id INT, share_user_id INT, share_allow_edit INT)");
            st.execute("CREATE TABLE \"SubmitRecord\"(submit_id INT AUTO_INCREMENT PRIMARY KEY, submit_question_id INT, submit_ip_addr VARCHAR(64), submit_answer VARCHAR(1000))");
            st.execute("CREATE TABLE \"Answer\"(answer_id INT PRIMARY KEY, answer_question_id INT, answer_user_id INT, answer_ip_addr VARCHAR(64))");
            st.execute("CREATE TABLE \"CharAnswer\"(char_id INT PRIMARY KEY, char_answer_id INT, char_choice VARCHAR(50))");
            st.execute("CREATE TABLE \"TextAnswer\"(text_id INT PRIMARY KEY, text_answer_id INT, text_content CLOB)");
            st.execute("CREATE TABLE \"QuestionType\"(type_id INT PRIMARY KEY, type_name VARCHAR(100))");
            st.execute("INSERT INTO \"QuestionType\"(type_id,type_name) VALUES(1,'SINGLE'),(2,'TEXT')");
        }
    }
}
