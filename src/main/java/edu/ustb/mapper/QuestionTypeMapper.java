package edu.ustb.mapper;

import edu.ustb.model.QuestionType;
import org.apache.ibatis.annotations.Select;

public interface QuestionTypeMapper {
    @Select("SELECT * FROM question_type WHERE question_type_id = #{id}")
    QuestionType findById(Integer id);
}