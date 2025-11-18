package edu.ustb.service;

import edu.ustb.mapper.*;
import edu.ustb.model.*;
import edu.ustb.test.TestMyBatisUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class ScoreboardServiceTest {
    static SqlSessionFactory factory;

    @BeforeAll
    static void setup() throws Exception { factory = TestMyBatisUtil.buildFactory(); }

    @Test
    void scoreboardAggregatesScores() throws Exception {
        // 建立问卷、题目、规则、提交
        int surveyId = 7001;
        try(SqlSession s = factory.openSession(true)) {
            SurveyMapper sm = s.getMapper(SurveyMapper.class);
            QuestionMapper qm = s.getMapper(QuestionMapper.class);
            ScoreRuleMapper srm = s.getMapper(ScoreRuleMapper.class);
            SubmitRecordMapper rm = s.getMapper(SubmitRecordMapper.class);
            Survey survey = new Survey();
            survey.setSurveyId(surveyId); survey.setSurveyTitle("S"); survey.setSurveyOwnerId(1); survey.setSurveyScoreboard(0); survey.setSurveyShowScore(1); survey.setSurveyCreateTime(LocalDate.now());
            sm.insert(survey);
            ScoreRule rule = new ScoreRule();
            rule.setRuleId(9001); rule.setRuleOwnerId(1); rule.setRuleJsFunc("function score(a){return a==='OK'?10:0;}"); rule.setRuleFullScore(10f); srm.insert(rule);
            Question q = new Question();
            q.setQuestionId(8001); q.setQuestionSurveyId(surveyId); q.setQuestionContent("Q?"); q.setQuestionType(1); q.setQuestionScoreRuleId(rule.getRuleId()); qm.insert(q);
            // 提交
            SubmitRecord r1 = new SubmitRecord(); r1.setSubmitQuestionId(q.getQuestionId()); r1.setSubmitIpAddr("1.1.1.1"); r1.setSubmitAnswer("OK"); rm.insert(r1);
            SubmitRecord r2 = new SubmitRecord(); r2.setSubmitQuestionId(q.getQuestionId()); r2.setSubmitIpAddr("1.1.1.2"); r2.setSubmitAnswer("BAD"); rm.insert(r2);
            SubmitRecord r3 = new SubmitRecord(); r3.setSubmitQuestionId(q.getQuestionId()); r3.setSubmitIpAddr("1.1.1.1"); r3.setSubmitAnswer("OK"); rm.insert(r3);
        }
        ScoreboardService scoreboardService = new ScoreboardService(factory, new ScoreRuleExecutor());
        List<java.util.Map<String,Object>> board = scoreboardService.buildScoreboard(surveyId);
        assertThat(board).hasSize(2);
        // 1.1.1.1 应该有 20 分，总分第一
        assertThat(board.get(0).get("ip")).isEqualTo("1.1.1.1");
        assertThat((Double)board.get(0).get("total")).isEqualTo(20d);
    }
}
