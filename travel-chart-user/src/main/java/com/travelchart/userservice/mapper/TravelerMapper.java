package com.travelchart.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelchart.userservice.entity.Traveler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface TravelerMapper extends BaseMapper<Traveler> {

    @Select("SELECT * FROM tg_traveler WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Traveler> selectByUserId(@Param("userId") Long userId);
}
