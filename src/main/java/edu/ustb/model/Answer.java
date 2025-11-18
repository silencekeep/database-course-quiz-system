package edu.ustb.model;

public class Answer {
    private Integer answerId;
    private Integer answerQuestionId;
    private Integer answerStemOrderId;

    public Integer getAnswerId() { return answerId; }
    public void setAnswerId(Integer answerId) { this.answerId = answerId; }
    public Integer getAnswerQuestionId() { return answerQuestionId; }
    public void setAnswerQuestionId(Integer answerQuestionId) { this.answerQuestionId = answerQuestionId; }
    public Integer getAnswerStemOrderId() { return answerStemOrderId; }
    public void setAnswerStemOrderId(Integer answerStemOrderId) { this.answerStemOrderId = answerStemOrderId; }
}