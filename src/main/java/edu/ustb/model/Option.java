package edu.ustb.model;

public class Option {
    private Integer optionId;
    private Integer optionQuestionId;
    private Integer optionOrderId;
    private String optionLabel;
    private String optionContent;

    public Integer getOptionId() { return optionId; }
    public void setOptionId(Integer optionId) { this.optionId = optionId; }
    public Integer getOptionQuestionId() { return optionQuestionId; }
    public void setOptionQuestionId(Integer optionQuestionId) { this.optionQuestionId = optionQuestionId; }
    public Integer getOptionOrderId() { return optionOrderId; }
    public void setOptionOrderId(Integer optionOrderId) { this.optionOrderId = optionOrderId; }
    public String getOptionLabel() { return optionLabel; }
    public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }
    public String getOptionContent() { return optionContent; }
    public void setOptionContent(String optionContent) { this.optionContent = optionContent; }
}