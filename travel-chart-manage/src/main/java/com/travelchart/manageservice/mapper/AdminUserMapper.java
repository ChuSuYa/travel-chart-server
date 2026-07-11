package com.travelchart.manageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelchart.manageservice.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
    @Select("SELECT * FROM tg_admin_user WHERE username = #{username} LIMIT 1")
    AdminUser findByUsername(String username);
}
