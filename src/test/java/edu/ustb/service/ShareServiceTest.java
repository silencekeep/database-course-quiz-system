package edu.ustb.service;

import edu.ustb.mapper.SurveyMapper;
import edu.ustb.model.Survey;
import edu.ustb.test.TestMyBatisUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class ShareServiceTest {
    static SqlSessionFactory factory;

    @BeforeAll
    static void setup() throws Exception { factory = TestMyBatisUtil.buildFactory(); }

    @Test
    void createShare_requiresOwner() throws Exception {
        // 准备一条问卷
        Survey survey = new Survey();
        survey.setSurveyId(5001);
        survey.setSurveyTitle("T1");
        survey.setSurveyOwnerId(900);
        survey.setSurveyScoreboard(0); survey.setSurveyShowScore(1); survey.setSurveyCreateTime(java.time.LocalDate.now());
        try(SqlSession s=factory.openSession(true)) { s.getMapper(SurveyMapper.class).insert(survey); }

        ShareService shareService = new ShareService(factory);
        // 非 owner
        assertThatThrownBy(() -> shareService.createShare(5001, 901, true)).hasMessage("FORBIDDEN");
        // owner 成功
        String link = shareService.createShare(5001, 900, true);
        assertThat(link).contains("/api/surveys/5001?");
    }
}
