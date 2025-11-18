package edu.ustb.mapper;

import edu.ustb.model.Answer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;

public interface AnswerMapper {
    @Insert("INSERT INTO answer(answer_id,answer_question_id,answer_stem_order_id) VALUES(#{answerId},#{answerQuestionId},#{answerStemOrderId})")
    int insert(Answer a);

    @Delete("DELETE FROM answer WHERE answer_question_id IN (SELECT question_id FROM question WHERE question_survey_id=#{surveyId})")
    int deleteBySurvey(Integer surveyId);
}