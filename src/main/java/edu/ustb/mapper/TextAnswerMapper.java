package edu.ustb.mapper;

import edu.ustb.model.TextAnswer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;

public interface TextAnswerMapper {
    @Insert("INSERT INTO text_answer(answer_id,answer_text) VALUES(#{answerId},#{answerText})")
    int insert(TextAnswer t);

    @Delete("DELETE FROM text_answer WHERE answer_id IN (SELECT answer_id FROM answer WHERE answer_question_id IN (SELECT question_id FROM question WHERE question_survey_id=#{surveyId}))")
    int deleteBySurvey(Integer surveyId);
}