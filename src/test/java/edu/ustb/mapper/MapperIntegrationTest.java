package edu.ustb.mapper;

import edu.ustb.model.*;
import edu.ustb.test.TestMyBatisUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MapperIntegrationTest {
    static SqlSessionFactory factory;

    @BeforeAll
    static void init() throws Exception { factory = TestMyBatisUtil.buildFactory(); }

    @Test
    void testInsertSurveyCascadeQuestionsOptionsRules() {
        int surveyId = newId();
        try(SqlSession session = factory.openSession(true)) {
            SurveyMapper sm = session.getMapper(SurveyMapper.class);
            ScoreRuleMapper rm = session.getMapper(ScoreRuleMapper.class);
            QuestionMapper qm = session.getMapper(QuestionMapper.class);
            OptionMapper om = session.getMapper(OptionMapper.class);
            // survey
            Survey s = new Survey(); s.setSurveyId(surveyId); s.setSurveyTitle("M1"); s.setSurveyOwnerId(1); s.setSurveyCreateTime(LocalDate.now()); sm.insert(s);
            // rule
            ScoreRule r = new ScoreRule(); r.setRuleId(newId()); r.setRuleOwnerId(1); r.setRuleJsFunc("function score(a){return 1;}"); rm.insert(r);
            // question
            Question q = new Question(); q.setQuestionId(newId()); q.setQuestionSurveyId(surveyId); q.setQuestionContent("Q1"); q.setQuestionType(1); q.setQuestionScoreRuleId(r.getRuleId()); qm.insert(q);
            // options
            for(int i=0;i<2;i++){ Option opt = new Option(); opt.setOptionId(newId()); opt.setOptionQuestionId(q.getQuestionId()); opt.setOptionOrderId(i+1); opt.setOptionLabel(String.valueOf((char)('A'+i))); opt.setOptionContent("C"+i); om.insert(opt);}            
        }
        try(SqlSession session = factory.openSession()) {
            List<Question> list = session.getMapper(QuestionMapper.class).findBySurvey(surveyId);
            assertEquals(1, list.size());
            List<Option> opts = session.getMapper(OptionMapper.class).findByQuestion(list.get(0).getQuestionId());
            assertEquals(2, opts.size());
        }
    }

    @Test
    void testFindSharedToUser() {
        int surveyId = newId();
        try(SqlSession session = factory.openSession(true)) {
            SurveyMapper sm = session.getMapper(SurveyMapper.class);
            SurveyShareMapper shm = session.getMapper(SurveyShareMapper.class);
            Survey s = new Survey(); s.setSurveyId(surveyId); s.setSurveyTitle("ShareTest"); s.setSurveyOwnerId(2); s.setSurveyCreateTime(LocalDate.now()); sm.insert(s);
            for(int u: new int[]{10,11}) { SurveyShare sh = new SurveyShare(); sh.setShareId(newId()); sh.setShareSurveyId(surveyId); sh.setShareUserId(u); sh.setShareAllowEdit(0); shm.insert(sh);}            
        }
        try(SqlSession session = factory.openSession()) {
            List<Survey> shared = session.getMapper(SurveyMapper.class).findSharedToUser(11);
            assertEquals(1, shared.size());
            assertEquals("ShareTest", shared.get(0).getSurveyTitle());
        }
    }

    private static int newId(){ return (int)(System.nanoTime() & 0x7FFFFFFF); }
}
