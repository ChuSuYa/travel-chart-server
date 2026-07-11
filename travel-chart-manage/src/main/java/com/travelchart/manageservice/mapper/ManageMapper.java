package com.travelchart.manageservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ManageMapper {
    @Select("SELECT COUNT(*) FROM tg_user")
    long countUsers();

    @Select("SELECT COUNT(*) FROM tg_user WHERE status = 1")
    long countActiveUsers();

    @Select("SELECT COUNT(*) FROM tg_plan")
    long countPlans();

    @Select("SELECT COUNT(*) FROM tg_poi WHERE status = 1")
    long countPublishedPois();

    @Select("SELECT user_id AS userId, phone, nickname, status, create_time AS createTime FROM tg_user "
            + "WHERE (#{keyword} IS NULL OR phone LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%')) "
            + "ORDER BY user_id DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> findUsers(@Param("keyword") String keyword, @Param("offset") long offset, @Param("size") long size);

    @Select("SELECT COUNT(*) FROM tg_user WHERE (#{keyword} IS NULL OR phone LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%'))")
    long countUsersByKeyword(@Param("keyword") String keyword);

    @Update("UPDATE tg_user SET status = #{status} WHERE user_id = #{userId}")
    int updateUserStatus(@Param("userId") long userId, @Param("status") int status);

    @Select("SELECT poi_id AS poiId, name, city, type, rating, price_level AS priceLevel, status, create_time AS createTime "
            + "FROM tg_poi WHERE (#{keyword} IS NULL OR name LIKE CONCAT('%', #{keyword}, '%') OR city LIKE CONCAT('%', #{keyword}, '%')) "
            + "ORDER BY poi_id DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> findPois(@Param("keyword") String keyword, @Param("offset") long offset, @Param("size") long size);

    @Select("SELECT COUNT(*) FROM tg_poi WHERE (#{keyword} IS NULL OR name LIKE CONCAT('%', #{keyword}, '%') OR city LIKE CONCAT('%', #{keyword}, '%'))")
    long countPoisByKeyword(@Param("keyword") String keyword);

    @Update("UPDATE tg_poi SET status = #{status} WHERE poi_id = #{poiId}")
    int updatePoiStatus(@Param("poiId") long poiId, @Param("status") int status);
}
