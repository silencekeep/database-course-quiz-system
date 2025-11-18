package edu.ustb.model;

import java.util.List;

public class Question {
    private Integer questionId;
    private Integer questionSurveyId;
    private String questionContent;
    private Integer questionType;
    private Integer questionScoreRuleId;
    private List<Option> options;

    public Integer getQuestionId() { return questionId; }
    public void setQuestionId(Integer questionId) { this.questionId = questionId; }
    public Integer getQuestionSurveyId() { return questionSurveyId; }
    public void setQuestionSurveyId(Integer questionSurveyId) { this.questionSurveyId = questionSurveyId; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
    public Integer getQuestionType() { return questionType; }
    public void setQuestionType(Integer questionType) { this.questionType = questionType; }
    public Integer getQuestionScoreRuleId() { return questionScoreRuleId; }
    public void setQuestionScoreRuleId(Integer questionScoreRuleId) { this.questionScoreRuleId = questionScoreRuleId; }
    public List<Option> getOptions() { return options; }
    public void setOptions(List<Option> options) { this.options = options; }
}