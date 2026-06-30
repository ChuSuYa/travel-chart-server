package com.travelchart.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelchart.userservice.entity.Recommend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RecommendMapper extends BaseMapper<Recommend> {

    @Select("SELECT * FROM tg_recommend WHERE status = 1 ORDER BY heat_score DESC")
    List<Recommend> selectEnabledRecommends();
}
