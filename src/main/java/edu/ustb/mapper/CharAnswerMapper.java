package edu.ustb.mapper;

import edu.ustb.model.CharAnswer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;

public interface CharAnswerMapper {
    @Insert("INSERT INTO char_answer(answer_id,answer_letter) VALUES(#{answerId},#{answerLetter})")
    int insert(CharAnswer c);

    @Delete("DELETE FROM char_answer WHERE answer_id IN (SELECT answer_id FROM answer WHERE answer_question_id IN (SELECT question_id FROM question WHERE question_survey_id=#{surveyId}))")
    int deleteBySurvey(Integer surveyId);
}