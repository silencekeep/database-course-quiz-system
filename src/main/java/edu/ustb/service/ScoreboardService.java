package edu.ustb.service;

import edu.ustb.mapper.QuestionMapper;
import edu.ustb.mapper.ScoreRuleMapper;
import edu.ustb.mapper.SubmitRecordMapper;
import edu.ustb.model.Question;
import edu.ustb.model.ScoreRule;
import edu.ustb.model.SubmitRecord;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.*;

public class ScoreboardService {
    private final SqlSessionFactory factory;
    private final ScoreRuleExecutor executor;

    public ScoreboardService(SqlSessionFactory factory, ScoreRuleExecutor executor) {
        this.factory = factory;
        this.executor = executor;
    }

    public List<Map<String, Object>> buildScoreboard(Integer surveyId) {
        Map<String, List<Double>> ipScores = new HashMap<>();
        try (SqlSession session = factory.openSession()) {
            QuestionMapper qm = session.getMapper(QuestionMapper.class);
            SubmitRecordMapper rm = session.getMapper(SubmitRecordMapper.class);
            ScoreRuleMapper srm = session.getMapper(ScoreRuleMapper.class);
            List<Question> questions = qm.findBySurvey(surveyId);
            Map<Integer, Question> questionMap = new HashMap<>();
            Map<Integer, ScoreRule> ruleCache = new HashMap<>();
            for (Question q : questions) questionMap.put(q.getQuestionId(), q);
            for (Question q : questions) {
                List<SubmitRecord> records = rm.findByQuestion(q.getQuestionId());
                ScoreRule rule = null;
                if (q.getQuestionScoreRuleId() != null) {
                    rule = ruleCache.computeIfAbsent(q.getQuestionScoreRuleId(), id -> srm.findById(id));
                }
                for (SubmitRecord r : records) {
                    double sc = 0d;
                    if (rule != null) {
                        sc = executor.eval(rule.getRuleJsFunc(), r.getSubmitAnswer());
                    }
                    ipScores.computeIfAbsent(r.getSubmitIpAddr(), k -> new ArrayList<>()).add(sc);
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : ipScores.entrySet()) {
            double sum = e.getValue().stream().mapToDouble(Double::doubleValue).sum();
            double avg = e.getValue().isEmpty() ? 0 : sum / e.getValue().size();
            Map<String, Object> row = new HashMap<>();
            row.put("ip", e.getKey());
            row.put("total", sum);
            row.put("avg", avg);
            result.add(row);
        }
        result.sort((a, b) -> Double.compare((Double) b.get("total"), (Double) a.get("total")));
        return result;
    }
}