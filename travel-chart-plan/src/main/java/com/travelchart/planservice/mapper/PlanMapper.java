package com.travelchart.planservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelchart.planservice.entity.TravelPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface PlanMapper extends BaseMapper<TravelPlan> {

    @Select("SELECT * FROM tg_travel_plan WHERE user_id = #{userId} AND status = #{status} ORDER BY update_time DESC")
    List<TravelPlan> selectByUserAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Select("SELECT * FROM tg_travel_plan WHERE user_id = #{userId} ORDER BY update_time DESC")
    List<TravelPlan> selectByUserId(@Param("userId") Long userId);
}
