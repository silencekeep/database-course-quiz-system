package edu.ustb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ustb.mapper.*;
import edu.ustb.model.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class SubmissionService {
    private final SqlSessionFactory factory;
    private final ScoreRuleExecutor executor;
    private final ObjectMapper mapper = new ObjectMapper();

    // 题型常量：1 单选 (CharAnswer) 2 文本(TextAnswer) 3 多选(以文本存储 "A,B")
    public static final int TYPE_SINGLE_CHOICE = 1;
    public static final int TYPE_TEXT = 2;
    public static final int TYPE_MULTI_CHOICE = 3;

    public SubmissionService(SqlSessionFactory factory, ScoreRuleExecutor executor) {
        this.factory = factory;
        this.executor = executor;
    }

    @SuppressWarnings("unchecked")
    public double submit(String json, String ip) throws IOException {
        Map<String,Object> root = mapper.readValue(json, Map.class);
        Integer questionId = (Integer) root.get("question_id");
        String answer = coerceAnswer(root.get("answer"));
        return submitOne(questionId, answer, ip);
    }

    public double submitOne(Integer questionId, String answer, String ip) {
        double score = 0d;
        try(SqlSession session = factory.openSession(true)) {
            Question q = session.getMapper(QuestionMapper.class).findById(questionId);
            if(q == null) return 0d;
            if(q.getQuestionScoreRuleId()!=null) {
                ScoreRule rule = session.getMapper(ScoreRuleMapper.class).findById(q.getQuestionScoreRuleId());
                if(rule != null) {
                    score = executor.eval(rule.getRuleId()==null?0:rule.getRuleId(), rule.getRuleJsFunc(), answer, rule.getRuleFullScore());
                }
            }
            // 写 Answer 主表
            Answer answerEntity = new Answer();
            answerEntity.setAnswerId(newId());
            answerEntity.setAnswerQuestionId(questionId);
            answerEntity.setAnswerStemOrderId(0); // 未使用
            session.getMapper(AnswerMapper.class).insert(answerEntity);
            // 分支写入子表
            if(q.getQuestionType() == TYPE_SINGLE_CHOICE) {
                CharAnswer ca = new CharAnswer();
                ca.setAnswerId(answerEntity.getAnswerId());
                ca.setAnswerLetter(answer != null && !answer.isEmpty()? answer.substring(0,1): null);
                session.getMapper(CharAnswerMapper.class).insert(ca);
            } else if(q.getQuestionType() == TYPE_TEXT || q.getQuestionType() == TYPE_MULTI_CHOICE) {
                TextAnswer ta = new TextAnswer();
                ta.setAnswerId(answerEntity.getAnswerId());
                ta.setAnswerText(answer);
                session.getMapper(TextAnswerMapper.class).insert(ta);
            }
            // 写提交记录
            SubmitRecord record = new SubmitRecord();
            record.setSubmitId(newId());
            record.setSubmitQuestionId(questionId);
            record.setSubmitAnswer(answer);
            record.setSubmitTime(LocalDate.now());
            record.setSubmitIpAddr(ip);
            session.getMapper(SubmitRecordMapper.class).insert(record);
        }
        return score;
    }

    @SuppressWarnings("unchecked")
    public double submitBatch(Integer surveyId, String jsonArrayStr, String ip) throws IOException {
        List<Map<String,Object>> list = mapper.readValue(jsonArrayStr, List.class);
        double total = 0d;
        for(Map<String,Object> item : list) {
            Integer qid = (Integer) item.get("question_id");
            String ans = coerceAnswer(item.get("answer"));
            total += submitOne(qid, ans, ip);
        }
        return total;
    }

    private String coerceAnswer(Object obj) {
        if(obj == null) return null;
        if(obj instanceof String) return (String) obj;
        if(obj instanceof List) {
            @SuppressWarnings("unchecked") List<Object> arr = (List<Object>) obj;
            return String.join(",", arr.stream().map(o -> o==null? "" : String.valueOf(o)).toArray(String[]::new));
        }
        return String.valueOf(obj);
    }

    private int newId() { return (int)(System.nanoTime() & 0x7FFFFFFF); }
}