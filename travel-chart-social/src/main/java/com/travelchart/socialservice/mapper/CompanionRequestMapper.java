package com.travelchart.socialservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travelchart.socialservice.entity.CompanionRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface CompanionRequestMapper extends BaseMapper<CompanionRequest> {

    @Select("SELECT * FROM tg_companion_request WHERE status = 'active' ORDER BY create_time DESC LIMIT #{limit}")
    List<CompanionRequest> selectActive(@Param("limit") int limit);
}
