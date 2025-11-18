package edu.ustb.model;

public class SurveyShare {
    private Integer shareId;
    private Integer shareSurveyId;
    private Integer shareUserId;
    private Integer shareAllowEdit;

    public Integer getShareId() { return shareId; }
    public void setShareId(Integer shareId) { this.shareId = shareId; }
    public Integer getShareSurveyId() { return shareSurveyId; }
    public void setShareSurveyId(Integer shareSurveyId) { this.shareSurveyId = shareSurveyId; }
    public Integer getShareUserId() { return shareUserId; }
    public void setShareUserId(Integer shareUserId) { this.shareUserId = shareUserId; }
    public Integer getShareAllowEdit() { return shareAllowEdit; }
    public void setShareAllowEdit(Integer shareAllowEdit) { this.shareAllowEdit = shareAllowEdit; }
}