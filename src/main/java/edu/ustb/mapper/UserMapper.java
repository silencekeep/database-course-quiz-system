package edu.ustb.mapper;

import edu.ustb.model.User;
import org.apache.ibatis.annotations.*;

public interface UserMapper {
    @Select("SELECT * FROM app_user WHERE user_id = #{id}")
    User findById(Integer id);

    @Select("SELECT * FROM app_user WHERE user_account = #{account}")
    User findByAccount(String account);

    @Insert("INSERT INTO app_user(user_id,user_account,user_name,user_password,user_register_time,user_last_login_time) VALUES(#{userId},#{userAccount},#{userName},#{userPassword},#{userRegisterTime},#{userLastLoginTime})")
    int insert(User user);

    @Update("UPDATE app_user SET user_last_login_time = #{time} WHERE user_id = #{userId}")
    int updateLastLogin(@Param("userId") Integer userId, @Param("time") java.time.LocalDate time);
}