package edu.ustb.model;

import java.time.LocalDate;
import java.util.List;

public class Survey {
    private Integer surveyId;
    private String surveyTitle;
    private Integer surveyOwnerId;
    private Integer surveyScoreboard;
    private Integer surveyShowScore;
    private LocalDate surveyCreateTime;
    private LocalDate surveyExpireTime;
    private String surveyDescription;
    private List<Question> questions;

    public Integer getSurveyId() { return surveyId; }
    public void setSurveyId(Integer surveyId) { this.surveyId = surveyId; }
    public String getSurveyTitle() { return surveyTitle; }
    public void setSurveyTitle(String surveyTitle) { this.surveyTitle = surveyTitle; }
    public Integer getSurveyOwnerId() { return surveyOwnerId; }
    public void setSurveyOwnerId(Integer surveyOwnerId) { this.surveyOwnerId = surveyOwnerId; }
    public Integer getSurveyScoreboard() { return surveyScoreboard; }
    public void setSurveyScoreboard(Integer surveyScoreboard) { this.surveyScoreboard = surveyScoreboard; }
    public Integer getSurveyShowScore() { return surveyShowScore; }
    public void setSurveyShowScore(Integer surveyShowScore) { this.surveyShowScore = surveyShowScore; }
    public LocalDate getSurveyCreateTime() { return surveyCreateTime; }
    public void setSurveyCreateTime(LocalDate surveyCreateTime) { this.surveyCreateTime = surveyCreateTime; }
    public LocalDate getSurveyExpireTime() { return surveyExpireTime; }
    public void setSurveyExpireTime(LocalDate surveyExpireTime) { this.surveyExpireTime = surveyExpireTime; }
    public String getSurveyDescription() { return surveyDescription; }
    public void setSurveyDescription(String surveyDescription) { this.surveyDescription = surveyDescription; }
    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }
}