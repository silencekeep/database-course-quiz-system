package edu.ustb.mapper;

import edu.ustb.model.SurveyShare;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SurveyShareMapper {
    @Insert("INSERT INTO survey_share(share_id,share_survey_id,share_user_id,share_allow_edit) VALUES(#{shareId},#{shareSurveyId},#{shareUserId},#{shareAllowEdit})")
    int insert(SurveyShare share);

    @Select("SELECT * FROM survey_share WHERE share_survey_id = #{surveyId}")
    List<SurveyShare> findBySurvey(Integer surveyId);

    @Select("SELECT COUNT(*) FROM survey_share WHERE share_survey_id=#{surveyId}")
    int countBySurvey(Integer surveyId);

    @Select("SELECT * FROM survey_share WHERE share_survey_id = #{surveyId} ORDER BY share_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<SurveyShare> pageBySurvey(@Param("surveyId") Integer surveyId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT * FROM survey_share WHERE share_user_id = #{userId}")
    List<SurveyShare> findByUser(Integer userId);

    @Delete("DELETE FROM survey_share WHERE share_survey_id=#{surveyId}")
    int deleteBySurvey(Integer surveyId);
}