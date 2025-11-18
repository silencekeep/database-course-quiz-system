package edu.ustb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ustb.mapper.*;
import edu.ustb.model.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class SurveyService {
    private final SqlSessionFactory factory;
    private final ObjectMapper mapper = new ObjectMapper();

    public SurveyService(SqlSessionFactory factory) {
        this.factory = factory;
    }

    public Survey createSurveyFromJson(String json) throws IOException {
        Map<String, Object> root = mapper.readValue(json, Map.class);
        try (SqlSession session = factory.openSession(false)) { // 手动控制事务
            Survey survey = new Survey();
            survey.setSurveyId(newId());
            survey.setSurveyTitle((String) root.get("survey_title"));
            if (root.get("survey_description") != null) {
                survey.setSurveyDescription((String) root.get("survey_description"));
            }
            // 描述字段（数据库若已有列 survey_description，可在 mapper 中扩展；这里先尝试从 root 取值放入临时 map）
            survey.setSurveyOwnerId((Integer) root.getOrDefault("creator_id", 0));
            survey.setSurveyScoreboard(0);
            survey.setSurveyShowScore(1);
            survey.setSurveyCreateTime(LocalDate.now());
            survey.setSurveyExpireTime(null);

            List<Map<String, Object>> questionList = (List<Map<String, Object>>) root.get("questions");
            List<Question> questions = new ArrayList<>();
            ScoreRuleMapper scoreRuleMapper = session.getMapper(ScoreRuleMapper.class);
            QuestionMapper qm = session.getMapper(QuestionMapper.class);
            OptionMapper om = session.getMapper(OptionMapper.class);
            session.getMapper(SurveyMapper.class).insert(survey);
            if (questionList != null) {
                for (Map<String, Object> qMap : questionList) {
                    Question q = new Question();
                    q.setQuestionId(newId());
                    q.setQuestionSurveyId(survey.getSurveyId());
                    q.setQuestionContent((String) qMap.get("question_content"));
                    Number typeNum = (Number) qMap.getOrDefault("question_type", 1);
                    q.setQuestionType(typeNum.intValue());
                    Map<String, Object> scoreRule = (Map<String, Object>) qMap.get("score_rule");
                    if (scoreRule != null) {
                        ScoreRule rule = new ScoreRule();
                        rule.setRuleId(newId());
                        rule.setRuleOwnerId(survey.getSurveyOwnerId());
                        rule.setRuleJsFunc((String) scoreRule.get("rule_js_func"));
                        Number full = (Number) scoreRule.getOrDefault("full_score", 0);
                        rule.setRuleFullScore(full.floatValue());
                        scoreRuleMapper.insert(rule);
                        q.setQuestionScoreRuleId(rule.getRuleId());
                    }
                    qm.insert(q);
                    List<Map<String, Object>> opts = (List<Map<String, Object>>) qMap.get("options");
                    if (opts != null) {
                        int order = 1;
                        for (Map<String, Object> omap : opts) {
                            Option op = new Option();
                            op.setOptionId(newId());
                            op.setOptionQuestionId(q.getQuestionId());
                            op.setOptionOrderId(order++);
                            op.setOptionLabel((String) omap.get("label"));
                            op.setOptionContent((String) omap.get("content"));
                            om.insert(op);
                        }
                    }
                    questions.add(q);
                }
            }
            survey.setQuestions(questions);
            session.commit();
            return survey;
        }
    }

    // 简单分页：limit/offset 由调用方传入（分别为 pageSize, (page-1)*pageSize）
    public List<Survey> listMySurveysPaged(Integer userId, int offset, int limit) {
        try (SqlSession s = factory.openSession()) {
            List<Survey> all = s.getMapper(SurveyMapper.class).findByOwner(userId);
            if (offset >= all.size()) return Collections.emptyList();
            int to = Math.min(all.size(), offset + limit);
            return all.subList(offset, to);
        }
    }

    public List<Survey> listSharedPaged(Integer userId, int offset, int limit) {
        try (SqlSession s = factory.openSession()) {
            List<Survey> all = s.getMapper(SurveyMapper.class).findSharedToUser(userId);
            if (offset >= all.size()) return Collections.emptyList();
            int to = Math.min(all.size(), offset + limit);
            return all.subList(offset, to);
        }
    }

    public Survey loadSurvey(Integer id) {
        try (SqlSession session = factory.openSession()) {
            Survey s = session.getMapper(SurveyMapper.class).findById(id);
            if (s == null) return null;
            List<Question> qs = session.getMapper(QuestionMapper.class).findBySurvey(id);
            QuestionTypeMapper qtm = session.getMapper(QuestionTypeMapper.class);
            for (Question q : qs) {
                q.setOptions(session.getMapper(OptionMapper.class).findByQuestion(q.getQuestionId()));
                // 可选：加载题型描述 (未在Question类中存字段，这里仅示例，如需可扩展字段)
                qtm.findById(q.getQuestionType()); // 如需题型描述可在此扩展
            }
            s.setQuestions(qs);
            return s;
        }
    }

    public boolean updateSurveyBasic(Integer id, Integer userId, String title, String description) {
        try (SqlSession session = factory.openSession(true)) {
            SurveyMapper sm = session.getMapper(SurveyMapper.class);
            Survey s = sm.findById(id);
            if (s == null) return false;
            if (!s.getSurveyOwnerId().equals(userId)) throw new RuntimeException("FORBIDDEN");
            sm.updateTitleDesc(id, title, description);
            return true;
        }
    }

    // 全量替换结构：questions 数组，内部同 createSurveyFromJson 的格式
    @SuppressWarnings("unchecked")
    public boolean replaceStructure(Integer surveyId, Integer userId, String json) throws IOException {
        Map<String, Object> root = mapper.readValue(json, Map.class);
        List<Map<String, Object>> questionList = (List<Map<String, Object>>) root.get("questions");
        try (SqlSession session = factory.openSession(false)) {
            SurveyMapper sm = session.getMapper(SurveyMapper.class);
            Survey s = sm.findById(surveyId);
            if (s == null) return false;
            if (!s.getSurveyOwnerId().equals(userId)) throw new RuntimeException("FORBIDDEN");
            // 级联删除旧结构（不删除已有提交与答案，以保持历史成绩；若需清空可拓展参数）
            // 顺序：先删选项 -> 题目 -> 规则，避免 question.question_score_rule_id 外键指向 score_rule 时的删除冲突
            session.getMapper(OptionMapper.class).deleteBySurvey(surveyId);
            session.getMapper(QuestionMapper.class).deleteBySurvey(surveyId);
            session.getMapper(ScoreRuleMapper.class).deleteBySurvey(surveyId);
            // 重建
            if (questionList != null) {
                QuestionMapper qm = session.getMapper(QuestionMapper.class);
                OptionMapper om = session.getMapper(OptionMapper.class);
                ScoreRuleMapper ruleMapper = session.getMapper(ScoreRuleMapper.class);
                for (Map<String, Object> qMap : questionList) {
                    Question q = new Question();
                    q.setQuestionId(newId());
                    q.setQuestionSurveyId(surveyId);
                    q.setQuestionContent((String) qMap.get("question_content"));
                    Number typeNum = (Number) qMap.getOrDefault("question_type", 1);
                    q.setQuestionType(typeNum.intValue());
                    Map<String, Object> scoreRule = (Map<String, Object>) qMap.get("score_rule");
                    if (scoreRule != null) {
                        ScoreRule r = new ScoreRule();
                        r.setRuleId(newId());
                        r.setRuleOwnerId(s.getSurveyOwnerId());
                        r.setRuleJsFunc((String) scoreRule.get("rule_js_func"));
                        Number full = (Number) scoreRule.getOrDefault("full_score", 0);
                        r.setRuleFullScore(full.floatValue());
                        ruleMapper.insert(r);
                        q.setQuestionScoreRuleId(r.getRuleId());
                    }
                    qm.insert(q);
                    List<Map<String, Object>> opts = (List<Map<String, Object>>) qMap.get("options");
                    if (opts != null) {
                        int order = 1;
                        for (Map<String, Object> omap : opts) {
                            Option op = new Option();
                            op.setOptionId(newId());
                            op.setOptionQuestionId(q.getQuestionId());
                            op.setOptionOrderId(order++);
                            op.setOptionLabel((String) omap.get("label"));
                            op.setOptionContent((String) omap.get("content"));
                            om.insert(op);
                        }
                    }
                }
            }
            session.commit();
            return true;
        }
    }

    public boolean deleteSurvey(Integer id, Integer userId) {
        try (SqlSession session = factory.openSession(false)) {
            SurveyMapper sm = session.getMapper(SurveyMapper.class);
            Survey s = sm.findById(id);
            if (s == null) return false;
            if (!s.getSurveyOwnerId().equals(userId)) throw new RuntimeException("FORBIDDEN");
            // 顺序：提交记录->答案子表->答案主表->选项->题目->规则->分享->问卷
            session.getMapper(SubmitRecordMapper.class).deleteBySurvey(id);
            session.getMapper(CharAnswerMapper.class).deleteBySurvey(id);
            session.getMapper(TextAnswerMapper.class).deleteBySurvey(id);
            session.getMapper(AnswerMapper.class).deleteBySurvey(id);
            session.getMapper(OptionMapper.class).deleteBySurvey(id);
            session.getMapper(SurveyShareMapper.class).deleteBySurvey(id);
            session.getMapper(QuestionMapper.class).deleteBySurvey(id);
            session.getMapper(ScoreRuleMapper.class).deleteBySurvey(id);
            sm.deleteById(id);
            session.commit();
            return true;
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    private int newId() {
        return (int) (System.nanoTime() & 0x7FFFFFFF);
    }
}