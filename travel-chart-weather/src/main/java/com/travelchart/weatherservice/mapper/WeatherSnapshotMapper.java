package com.travelchart.weatherservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelchart.weatherservice.entity.WeatherSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WeatherSnapshotMapper extends BaseMapper<WeatherSnapshot> {

    @Select("SELECT * FROM tg_weather_snapshot WHERE plan_id = #{planId} AND city = #{city} ORDER BY forecast_date")
    List<WeatherSnapshot> selectByPlanAndCity(@Param("planId") Long planId, @Param("city") String city);
}
