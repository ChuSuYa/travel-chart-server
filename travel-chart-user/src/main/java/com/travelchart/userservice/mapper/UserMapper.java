package com.travelchart.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelchart.userservice.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM tg_user WHERE phone = #{phone} AND status = 1")
    User selectByPhone(@Param("phone") String phone);
}
