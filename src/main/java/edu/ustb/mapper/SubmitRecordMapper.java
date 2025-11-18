package edu.ustb.mapper;

import edu.ustb.model.SubmitRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

public interface SubmitRecordMapper {
    @Insert("INSERT INTO submit_record(submit_id,submit_time,submit_question_id,submit_answer,submit_ip_addr) VALUES(#{submitId},#{submitTime},#{submitQuestionId},#{submitAnswer},#{submitIpAddr})")
    int insert(SubmitRecord record);

    @Select("SELECT * FROM submit_record WHERE submit_question_id = #{questionId}")
    List<SubmitRecord> findByQuestion(Integer questionId);

    @Delete("DELETE FROM submit_record WHERE submit_question_id IN (SELECT question_id FROM question WHERE question_survey_id=#{surveyId})")
    int deleteBySurvey(Integer surveyId);
}