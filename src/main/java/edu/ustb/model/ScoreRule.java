package edu.ustb.model;

public class ScoreRule {
    private Integer ruleId;
    private Integer ruleOwnerId;
    private String ruleJsFunc;
    private Float ruleFullScore;

    public Integer getRuleId() { return ruleId; }
    public void setRuleId(Integer ruleId) { this.ruleId = ruleId; }
    public Integer getRuleOwnerId() { return ruleOwnerId; }
    public void setRuleOwnerId(Integer ruleOwnerId) { this.ruleOwnerId = ruleOwnerId; }
    public String getRuleJsFunc() { return ruleJsFunc; }
    public void setRuleJsFunc(String ruleJsFunc) { this.ruleJsFunc = ruleJsFunc; }
    public Float getRuleFullScore() { return ruleFullScore; }
    public void setRuleFullScore(Float ruleFullScore) { this.ruleFullScore = ruleFullScore; }
}