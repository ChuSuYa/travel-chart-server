package com.travelchart.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelchart.userservice.entity.Poi;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PoiMapper extends BaseMapper<Poi> {
}
