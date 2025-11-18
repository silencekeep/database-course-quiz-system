package edu.ustb.service;

import edu.ustb.mapper.*;
import edu.ustb.model.*;
import edu.ustb.test.TestMyBatisUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SubmissionService submit & submitBatch logic including scoring paths.
 */
public class SubmissionServiceTest {
    static SqlSessionFactory factory;
    static SubmissionService submissionService;
    static ScoreRuleExecutor executor;
    static int surveyId; // created in setup
    static int choiceQuestionId;
    static int textQuestionId;
    static int ruleId;

    @BeforeAll
    static void init() {
        try {
            factory = TestMyBatisUtil.buildFactory();
        } catch (Exception e) { throw new RuntimeException(e); }
        executor = new ScoreRuleExecutor();
        submissionService = new SubmissionService(factory, executor);
        try(SqlSession session = factory.openSession(true)) {
            // create survey
            SurveyMapper sm = session.getMapper(SurveyMapper.class);
            QuestionMapper qm = session.getMapper(QuestionMapper.class);
            OptionMapper om = session.getMapper(OptionMapper.class);
            ScoreRuleMapper rm = session.getMapper(ScoreRuleMapper.class);
            Survey survey = new Survey();
            surveyId = (int)(System.nanoTime() & 0x7FFFFFFF);
            survey.setSurveyId(surveyId);
            survey.setSurveyTitle("Test Survey");
            survey.setSurveyOwnerId(100);
            survey.setSurveyCreateTime(LocalDate.now());
            sm.insert(survey);
            // rule: answer === 'A' ? 5 : 0
            ScoreRule rule = new ScoreRule();
            ruleId = (int)(System.nanoTime() & 0x7FFFFFFF);
            rule.setRuleId(ruleId);
            // ScoreRule 模型无 surveyId 字段，测试中仅需 ownerId
            rule.setRuleOwnerId(100);
            rule.setRuleJsFunc("function score(answer){ if(answer==='A') return 10; return 0; }"); // 脚本返回10
            rule.setRuleFullScore(5f); // 但设置满分=5，用于测试截断
            rm.insert(rule);
            // single choice question
            Question cq = new Question();
            choiceQuestionId = (int)(System.nanoTime() & 0x7FFFFFFF);
            cq.setQuestionId(choiceQuestionId);
            cq.setQuestionSurveyId(surveyId);
            cq.setQuestionContent("Choose one");
            cq.setQuestionType(SubmissionService.TYPE_SINGLE_CHOICE);
            cq.setQuestionScoreRuleId(ruleId);
            qm.insert(cq);
            // options
            for(char c='A'; c<='C'; c++) {
                Option opt = new Option();
                opt.setOptionId((int)(System.nanoTime() & 0x7FFFFFFF));
                opt.setOptionQuestionId(choiceQuestionId);
                opt.setOptionContent("Option " + c);
                opt.setOptionLabel(String.valueOf(c));
                om.insert(opt);
            }
            // text question (no rule)
            Question tq = new Question();
            textQuestionId = (int)(System.nanoTime() & 0x7FFFFFFF);
            tq.setQuestionId(textQuestionId);
            tq.setQuestionSurveyId(surveyId);
            tq.setQuestionContent("Your text");
            tq.setQuestionType(SubmissionService.TYPE_TEXT);
            tq.setQuestionScoreRuleId(null);
            qm.insert(tq);
        }
    }

    @Test
    void testSubmitSingleChoiceCorrectScore5() throws Exception {
        String json = "{\"question_id\": "+choiceQuestionId+", \"answer\": \"A\"}";
        double score = submissionService.submit(json, "127.0.0.1");
        assertEquals(5d, score, 0.0001);
    }

    @Test
    void testSubmitSingleChoiceWrongScore0() throws Exception {
        String json = "{\"question_id\": "+choiceQuestionId+", \"answer\": \"B\"}";
        double score = submissionService.submit(json, "127.0.0.1");
        assertEquals(0d, score, 0.0001);
    }

    @Test
    void testSubmitTextQuestionNoRuleScore0() {
        double score = submissionService.submitOne(textQuestionId, "hello", "127.0.0.1");
        assertEquals(0d, score, 0.0001);
    }

    @Test
    void testSubmitBatchMixedSum() throws Exception {
    String arr = "[" +
        "{\"question_id\": " + choiceQuestionId + ", \"answer\": \"A\"}," +
        "{\"question_id\": " + choiceQuestionId + ", \"answer\": \"B\"}," +
        "{\"question_id\": " + textQuestionId + ", \"answer\": \"text\"}" +
        "]";
        double total = submissionService.submitBatch(surveyId, arr, "127.0.0.1");
        assertEquals(5d, total, 0.0001);
    }

    @Test
    void testSubmitInvalidQuestionIdReturns0() {
        double score = submissionService.submitOne(-999, "X", "127.0.0.1");
        assertEquals(0d, score, 0.0001);
    }

    @Test
    void testRuleScriptThrowsReturns0() {
        // insert a question with broken rule
        int brokenRuleId = (int)(System.nanoTime() & 0x7FFFFFFF);
        int qid = (int)(System.nanoTime() & 0x7FFFFFFF);
        try(SqlSession session = factory.openSession(true)) {
            ScoreRuleMapper rm = session.getMapper(ScoreRuleMapper.class);
            QuestionMapper qm = session.getMapper(QuestionMapper.class);
            ScoreRule br = new ScoreRule(); br.setRuleId(brokenRuleId); br.setRuleOwnerId(100); br.setRuleJsFunc("function score(answer){ throw new Error('bad'); }"); rm.insert(br);
            Question q = new Question(); q.setQuestionId(qid); q.setQuestionSurveyId(surveyId); q.setQuestionContent("Broken rule question"); q.setQuestionType(SubmissionService.TYPE_SINGLE_CHOICE); q.setQuestionScoreRuleId(brokenRuleId); qm.insert(q);
        }
        double score = submissionService.submitOne(qid, "A", "127.0.0.1");
        assertEquals(0d, score, 0.0001);
    }
}
