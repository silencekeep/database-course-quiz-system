package edu.ustb.mapper;

import edu.ustb.model.Survey;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface SurveyMapper {
    @Select("SELECT * FROM survey WHERE survey_id = #{id}")
    Survey findById(Integer id);

    @Select("SELECT * FROM survey WHERE survey_owner_id = #{ownerId}")
    List<Survey> findByOwner(Integer ownerId);

    @Select("SELECT COUNT(*) FROM survey WHERE survey_owner_id=#{ownerId}")
    int countByOwner(Integer ownerId);

    @Select("SELECT * FROM survey WHERE survey_owner_id = #{ownerId} ORDER BY survey_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Survey> pageByOwner(@Param("ownerId") Integer ownerId, @Param("limit") int limit, @Param("offset") int offset);

    // 查询被分享给指定用户的问卷（去重）
    @Select("SELECT DISTINCT s.* FROM survey s JOIN survey_share sh ON s.survey_id = sh.share_survey_id WHERE sh.share_user_id = #{userId}")
    List<Survey> findSharedToUser(Integer userId);

    @Select("SELECT COUNT(DISTINCT s.survey_id) FROM survey s JOIN survey_share sh ON s.survey_id = sh.share_survey_id WHERE sh.share_user_id = #{userId}")
    int countSharedToUser(Integer userId);

    @Select("SELECT DISTINCT s.* FROM survey s JOIN survey_share sh ON s.survey_id = sh.share_survey_id WHERE sh.share_user_id = #{userId} ORDER BY s.survey_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Survey> pageSharedToUser(@Param("userId") Integer userId, @Param("limit") int limit, @Param("offset") int offset);

    @Insert("INSERT INTO survey(survey_id,survey_title,survey_owner_id,survey_scoreboard,survey_show_score,survey_create_time,survey_expire_time,survey_description) VALUES(#{surveyId},#{surveyTitle},#{surveyOwnerId},#{surveyScoreboard},#{surveyShowScore},#{surveyCreateTime},#{surveyExpireTime},#{surveyDescription})")
    int insert(Survey survey);

    @Update("UPDATE survey SET survey_title=#{title}, survey_description=#{description} WHERE survey_id=#{id}")
    int updateTitleDesc(@Param("id") Integer id, @Param("title") String title, @Param("description") String description);

    @Delete("DELETE FROM survey WHERE survey_id=#{id}")
    int deleteById(Integer id);
}