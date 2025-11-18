package edu.ustb.mapper;

import edu.ustb.model.Option;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface OptionMapper {
    @Select("SELECT * FROM option_item WHERE option_question_id = #{questionId} ORDER BY option_order_id")
    List<Option> findByQuestion(Integer questionId);

    @Insert("INSERT INTO option_item(option_id,option_question_id,option_order_id,option_label,option_content) VALUES(#{optionId},#{optionQuestionId},#{optionOrderId},#{optionLabel},#{optionContent})")
    int insert(Option option);

    @Delete("DELETE FROM option_item WHERE option_question_id IN (SELECT question_id FROM question WHERE question_survey_id=#{surveyId})")
    int deleteBySurvey(Integer surveyId);
}