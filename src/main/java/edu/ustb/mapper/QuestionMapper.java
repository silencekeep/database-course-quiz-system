package edu.ustb.mapper;

import edu.ustb.model.Question;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface QuestionMapper {
    @Select("SELECT * FROM question WHERE question_id = #{id}")
    Question findById(Integer id);

    @Select("SELECT * FROM question WHERE question_survey_id = #{surveyId}")
    List<Question> findBySurvey(Integer surveyId);

    @Insert("INSERT INTO question(question_id,question_survey_id,question_content,question_type,question_score_rule_id) VALUES(#{questionId},#{questionSurveyId},#{questionContent},#{questionType},#{questionScoreRuleId})")
    int insert(Question q);

    @Delete("DELETE FROM question WHERE question_survey_id=#{surveyId}")
    int deleteBySurvey(Integer surveyId);
}