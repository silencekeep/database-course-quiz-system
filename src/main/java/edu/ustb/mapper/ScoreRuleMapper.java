package edu.ustb.mapper;

import edu.ustb.model.ScoreRule;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

public interface ScoreRuleMapper {
    @Select("SELECT * FROM score_rule WHERE rule_id = #{id}")
    ScoreRule findById(Integer id);

    @Insert("INSERT INTO score_rule(rule_id,rule_owner_id,rule_js_func,rule_full_score) VALUES(#{ruleId},#{ruleOwnerId},#{ruleJsFunc},#{ruleFullScore})")
    int insert(ScoreRule rule);

    @Delete("DELETE FROM score_rule WHERE rule_id IN (SELECT question_score_rule_id FROM question WHERE question_survey_id=#{surveyId} AND question_score_rule_id IS NOT NULL)")
    int deleteBySurvey(Integer surveyId);
}