package edu.ustb.model;

import java.time.LocalDate;

public class SubmitRecord {
    private Integer submitId;
    private LocalDate submitTime; // Or LocalDateTime if desired
    private Integer submitQuestionId;
    private String submitAnswer;
    private String submitIpAddr;

    public Integer getSubmitId() { return submitId; }
    public void setSubmitId(Integer submitId) { this.submitId = submitId; }
    public LocalDate getSubmitTime() { return submitTime; }
    public void setSubmitTime(LocalDate submitTime) { this.submitTime = submitTime; }
    public Integer getSubmitQuestionId() { return submitQuestionId; }
    public void setSubmitQuestionId(Integer submitQuestionId) { this.submitQuestionId = submitQuestionId; }
    public String getSubmitAnswer() { return submitAnswer; }
    public void setSubmitAnswer(String submitAnswer) { this.submitAnswer = submitAnswer; }
    public String getSubmitIpAddr() { return submitIpAddr; }
    public void setSubmitIpAddr(String submitIpAddr) { this.submitIpAddr = submitIpAddr; }
}