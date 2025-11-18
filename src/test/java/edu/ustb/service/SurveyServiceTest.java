package edu.ustb.service;

import edu.ustb.model.Survey;
import edu.ustb.test.TestMyBatisUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSessionFactory;

import static org.assertj.core.api.Assertions.*;

public class SurveyServiceTest {
    static SqlSessionFactory factory;

    @BeforeAll
    static void setup() throws Exception {
        factory = TestMyBatisUtil.buildFactory();
    }

    @Test
    void createSurvey_basicStructurePersisted() throws Exception {
        SurveyService service = new SurveyService(factory);
        String json = "{\n" +
                "  \"survey_title\": \"Demo\",\n" +
                "  \"creator_id\": 101,\n" +
                "  \"questions\": [\n" +
                "    {\n" +
                "      \"question_content\": \"Q1?\",\n" +
                "      \"question_type\": 1,\n" +
                "      \"options\": [ {\"label\":\"A\",\"content\":\"Yes\"}, {\"label\":\"B\",\"content\":\"No\"} ],\n" +
                "      \"score_rule\": { \"rule_js_func\": \"function score(a){return a==='A'?5:0;}\", \"full_score\":5 }\n" +
                "    },\n" +
                "    {\n" +
                "      \"question_content\": \"Q2?\",\n" +
                "      \"question_type\": 2\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        Survey survey = service.createSurveyFromJson(json);
        assertThat(survey.getSurveyId()).isNotNull();
        assertThat(survey.getQuestions()).hasSize(2);
        assertThat(survey.getQuestions().get(0).getOptions()).hasSize(2);
        assertThat(survey.getQuestions().get(0).getQuestionScoreRuleId()).isNotNull();
    }
}
